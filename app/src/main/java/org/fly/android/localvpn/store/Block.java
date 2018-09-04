package org.fly.android.localvpn.store;

import org.fly.android.localvpn.firewall.Firewall;
import org.fly.android.localvpn.Packet;

import java.nio.ByteBuffer;
import java.util.LinkedList;

public abstract class Block {
    public String ipAndPort;
    public Packet referencePacket;
    protected Firewall firewall;

    protected static final int MAX_CACHE_SIZE = 50; // XXX: Is this ideal?

    protected abstract void closeChannel();

    public Firewall getFirewall() {
        return firewall;
    }

    public LinkedList<ByteBuffer> filter(ByteBuffer byteBuffer)
    {
        firewall.write(byteBuffer);

        // 允许转发
        if (firewall.isAccept())
            return firewall.getCache();
            // 丢弃包
        else if (firewall.isDrop())
            firewall.clear();

        return null;
    }

    public LinkedList<ByteBuffer> getResponse() {
        return firewall.getResponse();
    }

}
