/*
** Copyright 2015, Mohamed Naufal
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package org.fly.protocol.cache;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ByteBufferPool
{
    public static final int BUFFER_SIZE = 16384; // 16K: Is this ideal?
    private static ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();

    public static ByteBuffer acquire()
    {
        synchronized(ByteBufferPool.class)
        {
            ByteBuffer buffer;
            // 预先分配10个
            if (pool.isEmpty()) {
                for (int i = 0; i < 10; i++) {
                    buffer = ByteBuffer.allocateDirect(BUFFER_SIZE); // Using DirectBuffer for zero-copy
                    pool.offer(buffer);
                }
            }

            return pool.poll();
        }
    }

    // 让GC处理吧，就目前来说，代码中释放的逻辑可能有问题
    // 某一些ByteBuffer不正确的释放导致数据被复写,
    public static void release(ByteBuffer buffer)
    {
        //buffer.clear();
        //pool.offer(buffer);
    }

    public static void clear()
    {
        pool.clear();
    }
}
