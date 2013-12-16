/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.transport.messages;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.UUID;

import com.google.common.collect.ImmutableMap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.exceptions.*;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.tracing.Tracing;
import org.apache.cassandra.transport.*;
import org.apache.cassandra.utils.UUIDGen;

/**
 * A CQL query
 */
public class QueryMessage extends Message.Request
{
    public static final Message.Codec<QueryMessage> codec = new Message.Codec<QueryMessage>()
    {
        public QueryMessage decode(ChannelBuffer body, int version)
        {
            String query = CBUtil.readLongString(body);
            if (version == 1)
            {
                ConsistencyLevel consistency = CBUtil.readConsistencyLevel(body);
                return new QueryMessage(query, QueryOptions.fromProtocolV1(consistency, Collections.<ByteBuffer>emptyList()));
            }
            else
            {
                return new QueryMessage(query, QueryOptions.codec.decode(body, version));
            }
        }

        public void encode(QueryMessage msg, ChannelBuffer dest, int version)
        {
            CBUtil.writeLongString(msg.query, dest);
            if (version == 1)
                CBUtil.writeConsistencyLevel(msg.options.getConsistency(), dest);
            else
                QueryOptions.codec.encode(msg.options, dest, version);
        }

        public int encodedSize(QueryMessage msg, int version)
        {
            int size = CBUtil.sizeOfLongString(msg.query);

            if (version == 1)
            {
                size += CBUtil.sizeOfConsistencyLevel(msg.options.getConsistency());
            }
            else
            {
                size += QueryOptions.codec.encodedSize(msg.options, version);
            }
            return size;
        }
    };

    public final String query;
    public final QueryOptions options;

    public QueryMessage(String query, QueryOptions options)
    {
        super(Type.QUERY);
        this.query = query;
        this.options = options;
    }

    //QueryState的实例在org.apache.cassandra.transport.ServerConnection.validateNewMessage(Type, int, int)中生成
    public Message.Response execute(QueryState state)
    {
        try
        {
            if (options.getPageSize() == 0)
                throw new ProtocolException("The page size cannot be 0");

            UUID tracingId = null;
            if (isTracingRequested()) //生成一个tracingId，作为system_traces.sessions表session_id字段的值
            {
                tracingId = UUIDGen.getTimeUUID();
                state.prepareTracingSession(tracingId);
            }

            if (state.traceNextQuery())
            {
                state.createTracingSession();

                //builder会构建一个Map，作为system_traces.sessions表parameters字段的值
                ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
                builder.put("query", query);
                if (options.getPageSize() > 0)
                    builder.put("page_size", Integer.toString(options.getPageSize()));

                //往system_traces.sessions表插入一条记录
                Tracing.instance.begin("Execute CQL3 query", builder.build());
            }

            Message.Response response = QueryProcessor.process(query, state, options);
            if (options.skipMetadata() && response instanceof ResultMessage.Rows)
                ((ResultMessage.Rows)response).result.metadata.setSkipMetadata();

            if (tracingId != null)
                response.setTracingId(tracingId);

            return response;
        }
        catch (Exception e)
        {
            if (!((e instanceof RequestValidationException) || (e instanceof RequestExecutionException)))
                logger.error("Unexpected error during query", e);
            return ErrorMessage.fromException(e);
        }
        finally
        {
            //往system_traces.sessions表插入一条记录，实际上只填补duration字段
            //用来记录执行时间的长度
            Tracing.instance.stopSession();
        }
    }

    @Override
    public String toString()
    {
        return "QUERY " + query;
    }
}
