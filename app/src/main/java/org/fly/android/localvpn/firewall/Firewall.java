package org.fly.android.localvpn.firewall;

import android.util.Log;

import org.fly.android.localvpn.LocalVPN;
import org.fly.android.localvpn.Packet;
import org.fly.android.localvpn.contract.IFirewall;
import org.fly.android.localvpn.store.Block;
import org.fly.protocol.exception.RequestException;
import org.fly.protocol.exception.ResponseException;
import org.fly.protocol.http.request.Method;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Firewall {

    private static final String TAG = Firewall.class.getSimpleName();
    private static Filter filter;

    private static final int PROTOCOL_GRID = 0xa101;

    public static void createTable(String config) {
        filter = new Filter(config);
    }

    private enum Status {
        ACCEPT, // 放行
        DROP, // 丢包
        INCOMPLETE, //包不完整
    }

    private LinkedList<ByteBuffer> session = new LinkedList<>();
    private LinkedList<ByteBuffer> response = new LinkedList<>();

    private Status status = Status.INCOMPLETE;
    private IFirewall protocol = null;
    private static Other other = new Other();
    private long count = 0;
    private final Packet.IP4Header.TransportProtocol transportProtocol;
    private Block block;

    public Firewall(Packet.IP4Header.TransportProtocol transportProtocol, Block block) {

        this.transportProtocol = transportProtocol;
        this.block = block;
    }

    public boolean isAccept() {
        return status == Status.ACCEPT;
    }

    public boolean isDrop() {
        return status == Status.DROP;
    }

    public LinkedList<ByteBuffer> getSession() {
        return session;
    }

    public LinkedList<ByteBuffer> getResponse() {
        return response;
    }

    public void clear()
    {
        ByteBuffer buffer;
        while((buffer = session.poll()) != null) {
            //buffer.clear();
        }

        session.clear();
    }

    public void write(ByteBuffer byteBuffer) {

        ++count;

        ByteBuffer buffer = ByteBuffer.allocate(LocalVPN.BUFFER_SIZE);

        while (byteBuffer.hasRemaining())
            buffer.put(byteBuffer);

        buffer.flip();

        session.add(buffer.duplicate());

        handle(buffer.duplicate());
    }

    private String cacheToString()
    {
        StringBuilder stringBuilder = new StringBuilder();
        for (ByteBuffer buffer : session
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

    private void handle(ByteBuffer readableBuffer) {

        // 第一个包就可以判断出是什么协议
        // HTTP中，如果MTU短到 GET / 都无法一个包的场景, 就放行吧
        if (protocol == null)
        {
            if (transportProtocol == Packet.IP4Header.TransportProtocol.TCP
                    && Http.maybe(readableBuffer))
                protocol = new Http(this);
            else if (transportProtocol == Packet.IP4Header.TransportProtocol.UDP
                    && Dns.maybe(readableBuffer))
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
            LinkedList<ByteBuffer> results = protocol.write(readableBuffer);

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

    public static Filter getFilter() {
        return filter;
    }

    public Status getStatus() {
        return status;
    }

    public IFirewall getProtocol() {
        return protocol;
    }

    public long getCount() {
        return count;
    }

    public Block getBlock() {
        return block;
    }

    static class Filter {
        private Grid grid;
        private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        private Timer timer;

        public Filter(String config) {
            try {
                Grid grid = Grid.fromJson(Grid.class, config);
                setGrid(grid);
            } catch (IOException e)
            {
                Log.e(TAG, e.toString(), e);
            }

        }

        void setGrid(Grid grid)
        {
            readWriteLock.writeLock().lock();
            this.grid = grid;
            grid.init();
            readWriteLock.writeLock().unlock();
        }

        public String matchHttp(String url, Method method)
        {
            if (grid == null)
                return null;

            readWriteLock.readLock().lock();

            try {
                String result;

                result = grid.matchHttp(url, method);
                return result;

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                readWriteLock.readLock().unlock();
            }

            return null;
        }

        public List<String> matchDns(String domain, org.fly.protocol.dns.content.Dns.TYPE type)
        {
            if (grid == null)
                return null;

            readWriteLock.readLock().lock();

            try {
                List<String> list;

                list = grid.matchDns(domain, type);
                return list;

            } catch (Exception e) {
                e.printStackTrace();
            } finally
            {
                readWriteLock.readLock().unlock();
            }

            return null;
        }
    }
}

