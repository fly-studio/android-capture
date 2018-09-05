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

package org.fly.android.localvpn;

import android.util.Log;

import org.fly.android.localvpn.contract.UdpIO;
import org.fly.android.localvpn.store.UDB;
import org.fly.protocol.cache.ByteBufferPool;
import org.fly.protocol.dns.content.Dns;
import org.fly.protocol.dns.response.Response;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UDPInput extends UdpIO implements Runnable
{
    private static final String TAG = UDPInput.class.getSimpleName();

    public UDPInput(ConcurrentLinkedQueue<ByteBuffer> outputQueue, Selector selector)
    {
        this.outputQueue = outputQueue;
        this.selector = selector;
    }

    @Override
    public void run()
    {
        try
        {
            Log.i(TAG, "Started");
            while (!Thread.interrupted())
            {
                int readyChannels = selector.select();

                if (readyChannels == 0) {
                    Thread.sleep(10);
                    continue;
                }

                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = keys.iterator();

                while (keyIterator.hasNext() && !Thread.interrupted())
                {
                    SelectionKey key = keyIterator.next();
                    UDB udb = (UDB)key.attachment();
                    if (key.isValid() && key.isReadable())
                    {
                        keyIterator.remove();

                        ByteBuffer receiveBuffer = ByteBufferPool.acquire();
                        // Leave space for the header
                        receiveBuffer.position(HEADER_SIZE);

                        DatagramChannel inputChannel = (DatagramChannel) key.channel();
                        // XXX: We should handle any IOExceptions here immediately,
                        // but that probably won't happen with UDP
                        //int readBytes = inputChannel.read(receiveBuffer);

                        ByteBuffer buffer = ByteBufferPool.acquire();
                        int readBytes = inputChannel.read(buffer);
                        buffer.flip();

                        receiveBuffer.put(buffer);
                        ByteBufferPool.release(buffer);

                        Packet referencePacket = udb.referencePacket;
                        referencePacket.generateUDPBuffer(receiveBuffer, readBytes);
                        receiveBuffer.position(HEADER_SIZE + readBytes);

                        outputQueue.offer(receiveBuffer);
                    }
                }
            }
        }
        catch (InterruptedException e)
        {
            Log.i(TAG, "Stopping");
        }
        catch (IOException e)
        {
            Log.w(TAG, e.toString(), e);
        }
    }
}
