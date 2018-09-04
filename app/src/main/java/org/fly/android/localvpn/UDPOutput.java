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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UDPOutput extends UdpIO implements Runnable
{
    private static final String TAG = UDPOutput.class.getSimpleName();

    private LocalVPNService vpnService;


    public UDPOutput(ConcurrentLinkedQueue<Packet> inputQueue, Selector selector, LocalVPNService vpnService)
    {
        this.inputQueue = inputQueue;
        this.selector = selector;
        this.vpnService = vpnService;
    }

    @Override
    public void run()
    {
        Log.i(TAG, "Started");
        try
        {
            Thread currentThread = Thread.currentThread();
            while (true)
            {
                Packet currentPacket;
                // TODO: Block when not connected
                do
                {
                    currentPacket = inputQueue.poll();
                    if (currentPacket != null)
                        break;
                    Thread.sleep(10);
                } while (!currentThread.isInterrupted());

                if (currentThread.isInterrupted())
                    break;

                InetAddress destinationAddress = currentPacket.ip4Header.destinationAddress;
                int destinationPort = currentPacket.udpHeader.destinationPort;

                String ipAndPort = currentPacket.getKey();

                UDB udb = UDB.getUDB(ipAndPort);

                if (udb == null) {

                    DatagramChannel outputChannel = DatagramChannel.open();

                    udb = new UDB(ipAndPort, outputChannel, currentPacket);

                    vpnService.protect(outputChannel.socket());

                    try
                    {
                        outputChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));
                    }
                    catch (IOException e)
                    {
                        Log.e(TAG, "Connection error: " + ipAndPort, e);

                        UDB.closeUDB(udb);

                        ByteBufferPool.release(currentPacket.backingBuffer);
                        continue;
                    }
                    outputChannel.configureBlocking(false);
                    currentPacket.swapSourceAndDestination(); // 交换源和目标

                    selector.wakeup();
                    outputChannel.register(selector, SelectionKey.OP_READ, udb);

                    UDB.putUDB(ipAndPort, udb);
                }

                try
                {
                    ByteBuffer payloadBuffer = currentPacket.backingBuffer;

                    LinkedList<ByteBuffer> byteBuffers = udb.filter(payloadBuffer);
                    if (byteBuffers != null)
                    {
                        ByteBuffer buff;
                        while ((buff = byteBuffers.poll()) != null)
                            sendToRemote(udb, buff);
                    }

                }
                catch (IOException e)
                {
                    Log.e(TAG, "UDP Network write error: " + ipAndPort, e);

                    UDB.closeUDB(udb);
                }

                ByteBufferPool.release(currentPacket.backingBuffer);
            }
        }
        catch (InterruptedException e)
        {
            Log.i(TAG, "Stopping");
        }
        catch (IOException e)
        {
            Log.i(TAG, e.toString(), e);
        }
        finally
        {
            UDB.closeAll();
        }
    }

}
