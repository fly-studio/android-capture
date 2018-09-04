package org.fly.android.localvpn.contract;

import org.fly.android.localvpn.Packet;
import org.fly.android.localvpn.store.UDB;
import org.fly.protocol.cache.ByteBufferPool;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class UdpIO {

    protected static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE;
    protected Selector selector;
    protected ConcurrentLinkedQueue<ByteBuffer> outputQueue;
    protected ConcurrentLinkedQueue<Packet> inputQueue;

    /**
     * 发送数据给远端
     *
     * @param udb
     * @param remoteBuffer
     * @throws IOException
     */
    public void sendToRemote(UDB udb, ByteBuffer remoteBuffer) throws IOException
    {
        // 轉發數據給remote
        while (remoteBuffer.hasRemaining()) {
            udb.channel.write(remoteBuffer);
        }
    }

    /**
     * 回复本地客户端的数据
     *
     * @param udb
     * @param replyBuffer
     */
    public void sendToClient(UDB udb, ByteBuffer replyBuffer)
    {
        replyBuffer.flip();
        replyBuffer.position(0);

        Packet referencePacket = udb.referencePacket;

        int readBytes;
        //按照MTU分割
        while ((readBytes = Math.min(replyBuffer.limit() - replyBuffer.position(), Packet.MUTE_SIZE - HEADER_SIZE)) > 0)
        {
            ByteBuffer segmentBuffer = ByteBufferPool.acquire();

            segmentBuffer.position(HEADER_SIZE);
            segmentBuffer.put(replyBuffer.array(), replyBuffer.position(), readBytes);

            referencePacket.generateUDPBuffer(segmentBuffer, readBytes);

            replyBuffer.position(replyBuffer.position() + readBytes);

            segmentBuffer.position(HEADER_SIZE + readBytes);

            outputQueue.offer(segmentBuffer);
        }

    }

}
