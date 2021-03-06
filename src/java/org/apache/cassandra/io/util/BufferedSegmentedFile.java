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
package org.apache.cassandra.io.util;

//每次获取一个文件片段时实际上还是用RandomAccessReader打开同一个文件，只不过要seek到不到位置
public class BufferedSegmentedFile extends SegmentedFile
{
    public BufferedSegmentedFile(ChannelProxy channel, int bufferSize, long length)
    {
        super(new Cleanup(channel), channel, bufferSize, length);
    }

    private BufferedSegmentedFile(BufferedSegmentedFile copy)
    {
        super(copy);
    }

    public static class Builder extends SegmentedFile.Builder
    {
        public SegmentedFile complete(ChannelProxy channel, int bufferSize, long overrideLength)
        {
            long length = overrideLength > 0 ? overrideLength : channel.size();
            return new BufferedSegmentedFile(channel, bufferSize, length);
        }
    }

//<<<<<<< HEAD
//    //每次获取一个文件片段时实际上还是用RandomAccessReader打开同一个文件，只不过要seek到不到位置
//    public FileDataInput getSegment(long position)
//    {
//        RandomAccessReader reader = RandomAccessReader.open(channel, bufferSize, -1L);
//        reader.seek(position);
//        return reader;
//    }
//
//=======
//>>>>>>> 5bb80362d2d0533884f5a3af8892e6ff62d0bfff
    public BufferedSegmentedFile sharedCopy()
    {
        return new BufferedSegmentedFile(this);
    }
}
