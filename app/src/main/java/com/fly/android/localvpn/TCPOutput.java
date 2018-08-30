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

package com.fly.android.localvpn;

import android.util.Log;

import com.fly.android.localvpn.Packet.TCPHeader;
import com.fly.android.localvpn.TCB.TCBStatus;
import com.fly.android.localvpn.contract.TcpIO;
import com.fly.protocol.cache.ByteBufferPool;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPOutput extends TcpIO implements Runnable
{
    private static final String TAG = TCPOutput.class.getSimpleName();

    private LocalVPNService vpnService;

    private Random random = new Random();

    public TCPOutput(ConcurrentLinkedQueue<Packet> inputQueue, ConcurrentLinkedQueue<ByteBuffer> outputQueue,
                     Selector selector, LocalVPNService vpnService) throws IOException
    {
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
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
                    Thread.sleep(5);
                } while (!currentThread.isInterrupted());

                if (currentThread.isInterrupted())
                    break;

                ByteBuffer payloadBuffer = currentPacket.backingBuffer;
                currentPacket.backingBuffer = null;
                ByteBuffer responseBuffer = ByteBufferPool.acquire();

                InetAddress destinationAddress = currentPacket.ip4Header.destinationAddress;

                TCPHeader tcpHeader = currentPacket.tcpHeader;
                int destinationPort = tcpHeader.destinationPort;

                String ipAndPort = currentPacket.getKey();
                TCB tcb = TCB.getTCB(ipAndPort);
                if (tcb == null) // 握手1
                    initializeConnection(ipAndPort, destinationAddress, destinationPort,
                            currentPacket, tcpHeader, responseBuffer);
                else if (tcpHeader.isSYN()) // 客戶端還在發SYN？
                    processDuplicateSYN(tcb, tcpHeader, responseBuffer);
                else if (tcpHeader.isRST()) // 連接丟失
                    closeCleanly(tcb, responseBuffer);
                else if (tcpHeader.isFIN()) // 揮手1
                    processFIN(tcb, tcpHeader, responseBuffer);
                else if (tcpHeader.isACK())
                    processACK(tcb, tcpHeader, payloadBuffer, responseBuffer);

                // XXX: cleanup later
                if (responseBuffer.position() == 0)
                    ByteBufferPool.release(responseBuffer);
                ByteBufferPool.release(payloadBuffer);
            }
        }
        catch (InterruptedException e)
        {
            Log.i(TAG, "Stopping");
        }
        catch (IOException e)
        {
            Log.e(TAG, e.toString(), e);
        }
        finally
        {
            TCB.closeAll();
        }
    }

    private void initializeConnection(String ipAndPort, InetAddress destinationAddress, int destinationPort,
                                      Packet currentPacket, TCPHeader tcpHeader, ByteBuffer responseBuffer)
            throws IOException
    {
        currentPacket.swapSourceAndDestination();
        if (tcpHeader.isSYN())
        {
            SocketChannel outputChannel = SocketChannel.open();
            outputChannel.configureBlocking(false);
            vpnService.protect(outputChannel.socket());

            TCB tcb = new TCB(
                    ipAndPort,
                    random.nextInt(Short.MAX_VALUE + 1),
                    tcpHeader.sequenceNumber,
                    tcpHeader.sequenceNumber + 1,
                    tcpHeader.acknowledgementNumber,
                    outputChannel,
                    currentPacket);
            tcb.setTcpIO(this);

            TCB.putTCB(ipAndPort, tcb);

            try
            {
                outputChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));
                if (outputChannel.finishConnect())
                {
                    tcb.status = TCBStatus.SYN_RECEIVED;
                    // TODO: Set MSS for receiving larger packets from the device
                    currentPacket.generateTCPBuffer(responseBuffer, (byte) (TCPHeader.SYN | TCPHeader.ACK),
                            tcb.mySequenceNum, tcb.myAcknowledgementNum, 0);
                    tcb.mySequenceNum++; // SYN counts as a byte
                }
                else
                {
                    // register to selector
                    tcb.status = TCBStatus.SYN_SENT;
                    selector.wakeup();
                    tcb.selectionKey = outputChannel.register(selector, SelectionKey.OP_CONNECT, tcb);
                    return;
                }
            }
            catch (IOException e)
            {
                Log.e(TAG, "Connection error: " + ipAndPort, e);
                currentPacket.generateTCPBuffer(responseBuffer, (byte) TCPHeader.RST, 0, tcb.myAcknowledgementNum, 0);
                TCB.closeTCB(tcb);
            }
        }
        else
        {
            currentPacket.generateTCPBuffer(responseBuffer, (byte) TCPHeader.RST,
                    0, tcpHeader.sequenceNumber + 1, 0);
        }
        outputQueue.offer(responseBuffer);
    }

    /**
     * 客戶端重複發SYN，可能有問題
     * @param tcb
     * @param tcpHeader
     * @param responseBuffer
     */
    private void processDuplicateSYN(TCB tcb, TCPHeader tcpHeader, ByteBuffer responseBuffer)
    {
        synchronized (tcb)
        {
            if (tcb.status == TCBStatus.SYN_SENT)
            {
                tcb.myAcknowledgementNum = tcpHeader.sequenceNumber + 1;
                return;
            }
        }
        sendRST(tcb, 1, responseBuffer);
    }

    /**
     * 兩次揮手
     * @param tcb
     * @param tcpHeader
     * @param responseBuffer
     */
    private void processFIN(TCB tcb, TCPHeader tcpHeader, ByteBuffer responseBuffer)
    {
        synchronized (tcb)
        {
            Packet referencePacket = tcb.referencePacket;
            tcb.myAcknowledgementNum = tcpHeader.sequenceNumber + 1; //服务器(VPN)收到这个FIN，它发回一个ACK，确认序号为收到的序号加1。
            tcb.theirAcknowledgementNum = tcpHeader.acknowledgementNumber;

            // 客戶端揮手1, 回復ACK
            if (tcb.waitingForNetworkData)
            {
                tcb.status = TCBStatus.CLOSE_WAIT;
                referencePacket.generateTCPBuffer(responseBuffer, (byte) TCPHeader.ACK,
                        tcb.mySequenceNum, tcb.myAcknowledgementNum, 0);
            }
            // 客戶端揮手2+3,回復FIN+ACK
            else
            {
                tcb.status = TCBStatus.LAST_ACK;
                referencePacket.generateTCPBuffer(responseBuffer, (byte) (TCPHeader.FIN | TCPHeader.ACK),
                        tcb.mySequenceNum, tcb.myAcknowledgementNum, 0);
                tcb.mySequenceNum++; // FIN counts as a byte
            }
        }
        outputQueue.offer(responseBuffer);
    }

    /**
     * ACK
     *
     * @param tcb
     * @param tcpHeader
     * @param payloadBuffer
     * @param responseBuffer
     * @throws IOException
     */
    private void processACK(TCB tcb, TCPHeader tcpHeader, ByteBuffer payloadBuffer, ByteBuffer responseBuffer) throws IOException
    {
        int payloadSize = payloadBuffer.limit() - payloadBuffer.position();

        synchronized (tcb)
        {
            SocketChannel outputChannel = tcb.channel;

            // 客戶端握手3 ACK
            if (tcb.status == TCBStatus.SYN_RECEIVED)
            {
                tcb.status = TCBStatus.ESTABLISHED;

                // 註冊READ的Selector
                selector.wakeup();
                tcb.selectionKey = outputChannel.register(selector, SelectionKey.OP_READ, tcb);
                tcb.waitingForNetworkData = true;
            }
            // 客戶端最後回復的ACK，揮手4
            else if (tcb.status == TCBStatus.LAST_ACK)
            {
                closeCleanly(tcb, responseBuffer);
                return;
            }

            // 空ACK 可以不用轉發給remote， 因為空ACK是手机和VPN的通讯，VPN->Remote的通讯依赖于channel
            if (payloadSize == 0) return; // Empty ACK, ignore

            // 給selector 添加一個OP_READ監聽狀態
            if (!tcb.waitingForNetworkData)
            {
                selector.wakeup();
                tcb.selectionKey.interestOps(SelectionKey.OP_READ);
                tcb.waitingForNetworkData = true;
            }

            // TODO: We don't expect out-of-order packets, but verify
            // 回復給客戶端收到哪個ACK
            tcb.myAcknowledgementNum = tcpHeader.sequenceNumber + payloadSize;
            tcb.theirAcknowledgementNum = tcpHeader.acknowledgementNumber;
            Packet referencePacket = tcb.referencePacket;
            referencePacket.generateTCPBuffer(responseBuffer, (byte) TCPHeader.ACK, tcb.mySequenceNum, tcb.myAcknowledgementNum, 0);
            outputQueue.offer(responseBuffer);

            // 筛选数据，经过处理了之后再次转发
            // Forward to sendToRemote server
            try
            {
                LinkedList<ByteBuffer> byteBuffers = tcb.filter(payloadBuffer);
                if (byteBuffers == null)
                    return;

                ByteBuffer buff;
                while ((buff = byteBuffers.poll()) != null)
                    sendToRemote(tcb, buff);

            }
            catch (IOException e)
            {
                Log.e(TAG, "Network write error: " + tcb.ipAndPort, e);
                sendRST(tcb, payloadSize, responseBuffer);
                return;
            }
            catch (Exception e)
            {
                Log.e(TAG, e.getMessage(), e);
            }

        }
    }

    private void sendRST(TCB tcb, int prevPayloadSize, ByteBuffer buffer)
    {
        tcb.referencePacket.generateTCPBuffer(buffer, (byte) TCPHeader.RST, 0, tcb.myAcknowledgementNum + prevPayloadSize, 0);
        outputQueue.offer(buffer);
        TCB.closeTCB(tcb);
    }

    private void closeCleanly(TCB tcb, ByteBuffer buffer)
    {
        ByteBufferPool.release(buffer);
        TCB.closeTCB(tcb);
    }


}
