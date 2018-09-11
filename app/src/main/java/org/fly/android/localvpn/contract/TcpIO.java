package org.fly.android.localvpn.contract;

import org.fly.android.localvpn.Packet;
import org.fly.android.localvpn.store.TCB;
import org.fly.core.io.buffer.ByteBufferPool;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class TcpIO {

    protected static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE;

    protected ConcurrentLinkedQueue<Packet> inputQueue;
    protected ConcurrentLinkedQueue<ByteBuffer> outputQueue;
    protected Selector selector;

    /**
     * 发送数据给远端
     *
     * @param tcb
     * @param remoteBuffer
     * @throws IOException
     */
    public void sendToRemote(TCB tcb, ByteBuffer remoteBuffer) throws IOException
    {
        // 轉發數據給remote
        while (remoteBuffer.hasRemaining()) {
            tcb.channel.write(remoteBuffer);
        }
    }

    /**
     * 回复本地客户端的数据
     *
     * @param tcb
     * @param replyBuffer
     */
    public void sendToClient(TCB tcb, ByteBuffer replyBuffer)
    {
        replyBuffer.flip();
        Packet referencePacket = tcb.referencePacket;

        int readBytes;
        //按照MTU分割
        while ((readBytes = Math.min(replyBuffer.remaining(), Packet.MUTE_SIZE - HEADER_SIZE)) > 0)
        {
            ByteBuffer segmentBuffer = ByteBufferPool.acquire();

            segmentBuffer.position(HEADER_SIZE);

            byte[] bytes = new byte[readBytes];
            replyBuffer.get(bytes);
            segmentBuffer.put(bytes);

            referencePacket.generateTCPBuffer(segmentBuffer, (byte) (Packet.TCPHeader.PSH | Packet.TCPHeader.ACK),
                    tcb, readBytes);

            tcb.incrementSeq(readBytes); // Next sequence number
            segmentBuffer.position(HEADER_SIZE + readBytes);

            outputQueue.offer(segmentBuffer);
        }
    }
}
