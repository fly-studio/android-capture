package org.fly.android.localvpn.firewall;

import android.util.Log;

import org.fly.android.localvpn.Packet;
import org.fly.android.localvpn.contract.IFirewall;
import org.fly.protocol.cache.ByteBufferPool;
import org.fly.protocol.exception.RequestException;
import org.fly.protocol.exception.ResponseException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

public class Firewall {

    private static final String TAG = Firewall.class.getSimpleName();

    public static Table table;

    public static void createTable(String string) {
        table = new Table(string);
        table.tick();
    }

    private enum Status {
        ACCEPT, // 放行
        DROP, // 丢包
        INCOMPLETE, //包不完整
    }

    private LinkedList<ByteBuffer> cache = new LinkedList<>();
    private LinkedList<ByteBuffer> response = new LinkedList<>();

    private Status status = Status.INCOMPLETE;
    private IFirewall protocol = null;
    private static Other other = new Other();
    private long count = 0;
    private final Packet.IP4Header.TransportProtocol transportProtocol;

    public Firewall(Packet.IP4Header.TransportProtocol transportProtocol) {

        this.transportProtocol = transportProtocol;
    }

    public boolean isAccept() {
        return status == Status.ACCEPT;
    }

    public boolean isDrop() {
        return status == Status.DROP;
    }

    public LinkedList<ByteBuffer> getCache() {
        return cache;
    }

    public LinkedList<ByteBuffer> getResponse() {
        return response;
    }

    public void clear()
    {
        ByteBuffer buffer;
        while((buffer = cache.poll()) != null)
            ByteBufferPool.release(buffer);

        cache.clear();
    }

    public void write(ByteBuffer byteBuffer) {

        ++count;

        ByteBuffer buffer = ByteBufferPool.acquire();

        while (byteBuffer.hasRemaining())
            buffer.put(byteBuffer);

        buffer.flip();

        cache.add(buffer.duplicate());

        handle(buffer.duplicate());
    }

    private String cacheToString()
    {
        StringBuilder stringBuilder = new StringBuilder();
        for (ByteBuffer buffer : cache
                )
            stringBuilder.append(StandardCharsets.US_ASCII.decode(buffer.duplicate()));

        return stringBuilder.toString();
    }

    public void accept()
    {
        status = Status.ACCEPT;
    }

    public void drop()
    {
        status = Status.DROP;
    }

    private void handle(ByteBuffer buffer) {

        // 第一个包就可以判断出是什么协议
        // HTTP中，如果MTU短到 GET / 都无法一个包的场景, 就放行吧
        if (protocol == null)
        {
            if (transportProtocol == Packet.IP4Header.TransportProtocol.TCP
                    && Http.maybe(buffer))
                protocol = new Http(this);
            else if (transportProtocol == Packet.IP4Header.TransportProtocol.UDP
                    && Dns.maybe(buffer))
                protocol = new Dns(this);
            else
                protocol = other;
        }

        // 未知协议，直接放行
        if (protocol.equals(other)) {
            accept();
            return;
        }

        try
        {
            LinkedList<ByteBuffer> results = protocol.write(buffer);

            if (results != null)
                response.addAll(results);

        }
        catch (IOException | RequestException | ResponseException e)
        {
            protocol = other;
            accept();

            Log.e(TAG,  e.getMessage(), e);
        }

    }
}

