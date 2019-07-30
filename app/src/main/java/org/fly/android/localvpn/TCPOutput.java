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

import org.fly.android.localvpn.Packet.TCPHeader;
import org.fly.android.localvpn.contract.TcpIO;
import org.fly.android.localvpn.store.TCB;
import org.fly.android.localvpn.store.TCB.TCBStatus;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPOutput extends TcpIO implements Runnable
{
    private static final String TAG = TCPOutput.class.getSimpleName();

    private LocalVPNService vpnService;

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
                ByteBuffer responseBuffer = ByteBuffer.allocate(LocalVPN.BUFFER_SIZE);

                InetAddress destinationAddress = currentPacket.ip4Header.destinationAddress;

                TCPHeader tcpHeader = currentPacket.tcpHeader;
                int destinationPort = tcpHeader.destinationPort;

                String ipAndPort = currentPacket.getKey();
                TCB tcb = TCB.getTCB(ipAndPort);

                if (tcb == null) // 握手1
                    initializeConnection(ipAndPort, destinationAddress, destinationPort,
                            currentPacket, tcpHeader, responseBuffer);
                else if (tcpHeader.isSYN()) // 同步序列号
                    processDuplicateSYN(tcb, tcpHeader, responseBuffer);
                else if (tcpHeader.isRST()) // 連接丟失
                    closeCleanly(tcb, responseBuffer);
                else if (tcpHeader.isFIN()) // 揮手1
                    processFIN(tcb, tcpHeader, responseBuffer);
                else if (tcpHeader.isACK())
                    processACK(tcb, tcpHeader, payloadBuffer, responseBuffer);

                // XXX: cleanup later
                if (responseBuffer.position() == 0) {
                    //responseBuffer.clear();
                }
                //payloadBuffer.clear();
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
        catch (Exception e)
        {
            Log.e(TAG, e.toString(), e);
        }
        finally
        {
            Log.d(TAG, "Close all tcb");
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
                    tcpHeader,
                    outputChannel,
                    currentPacket);

            TCB.putTCB(ipAndPort, tcb);

            try
            {
                Log.d(TAG, "Connect: " + tcb.getIpAndPort());

                outputChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));

                //由于上面是异步的, 不可能这么快返回连接成功, 连接成功会触发selector的事件驱动
                //但是本地连接会快速的返回
                if (outputChannel.finishConnect())
                {
                    tcb.status = TCBStatus.SYN_RECEIVED;
                    // TODO: Set MSS for receiving larger packets from the device
                    currentPacket.generateTCPBuffer(responseBuffer, (byte) (TCPHeader.SYN | TCPHeader.ACK),
                            tcb, 0);

                    tcb.incrementSeq();// SYN counts as a byte
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
     * 客戶端發SYN
     * 如果在SYN_SENT状态下，则记录新的序列号
     * 其它状态，表示有问题
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

            //服务器(VPN)收到这个FIN，它发回一个ACK，确认序号为收到的序号加1。
            tcb.incrementReplyAck(tcpHeader);

            // 客戶端揮手1, 回復ACK
            if (tcb.waitingForNetworkData)
            {
                tcb.status = TCBStatus.CLOSE_WAIT;
                referencePacket.generateTCPBuffer(responseBuffer, (byte) TCPHeader.ACK,
                        tcb, 0);
            }
            // 客戶端揮手2,3,回復FIN+ACK
            else
            {
                tcb.status = TCBStatus.LAST_ACK;
                referencePacket.generateTCPBuffer(responseBuffer, (byte) (TCPHeader.FIN | TCPHeader.ACK),
                        tcb, 0);

                tcb.incrementSeq(); // FIN counts as a byte
            }
        }
        outputQueue.offer(responseBuffer);
    }

    /**
     * ACK
     * ACK + PSH
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

            // 空ACK 可以不用轉發給remote， 因為空ACK是手机和VPN的确认包，理论上需要验证seq，VPN->Remote的通讯依赖于channel
            if (payloadSize == 0) return; // Empty ACK, ignore

            // 給selector 添加一個OP_READ監聽狀態
            if (!tcb.waitingForNetworkData)
            {
                selector.wakeup();
                tcb.selectionKey.interestOps(SelectionKey.OP_READ);
                tcb.waitingForNetworkData = true;
            }

            // 筛选数据，经过处理了之后再次转发
            // Forward to sendToRemote server
            try
            {
                LinkedList<ByteBuffer> byteBuffers = tcb.filter(payloadBuffer);
                if (byteBuffers != null)
                {
                    ByteBuffer buff;
                    while ((buff = byteBuffers.poll()) != null)
                        sendToRemote(tcb, buff);
                }
            }
            catch (IOException e)
            {
                Log.e(TAG, "TCP Network write error: " + tcb.ipAndPort, e);
                sendRST(tcb, payloadSize, responseBuffer);
                return;
            }
            catch (Exception e)
            {
                Log.e(TAG, e.getMessage(), e);
            }

            // TODO: We don't expect out-of-order packets, but verify
            // 回復給客戶端收到哪個ACK
            tcb.incrementReplyAck(tcpHeader, payloadSize);

            Packet referencePacket = tcb.referencePacket;
            referencePacket.generateTCPBuffer(responseBuffer, (byte) TCPHeader.ACK, tcb, 0);
            outputQueue.offer(responseBuffer);

            // response before send to remote
            LinkedList<ByteBuffer> byteBuffers = tcb.getResponse();
            ByteBuffer buff;
            while ((buff = byteBuffers.poll()) != null)
                sendToClient(tcb, buff);

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
        //buffer.clear();

        TCB.closeTCB(tcb);
    }

}
