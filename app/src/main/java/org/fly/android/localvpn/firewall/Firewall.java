package org.fly.android.localvpn.firewall;

import android.util.Log;

import org.fly.android.localvpn.Packet;
import org.fly.android.localvpn.contract.IFirewall;
import org.fly.android.localvpn.store.Block;
import org.fly.core.io.buffer.ByteBufferPool;
import org.fly.core.text.json.Jsonable;
import org.fly.core.text.lp.Table;
import org.fly.core.text.lp.result.ResultProto;
import org.fly.protocol.exception.RequestException;
import org.fly.protocol.exception.ResponseException;
import org.fly.protocol.http.request.Method;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Firewall {

    private static final String TAG = Firewall.class.getSimpleName();
    private static Filter filter;

    private static final int PROTOCOL_GRID = 0xa001;

    public static void createTable(String host, int port) {
        filter = new Filter(host, port);
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
        while((buffer = session.poll()) != null)
            ByteBufferPool.release(buffer);

        session.clear();
    }

    public void write(ByteBuffer byteBuffer) {

        ++count;

        ByteBuffer buffer = ByteBufferPool.acquire();

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
        private Table table;
        private Table.Connection connection;
        private Grid grid;
        private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        private Timer timer;

        Filter(String host, int port) {
            table = new Table();

            connect(Table.decodeString(host), port);
            timer = new Timer();
            long intval = 10_000 + new Random().nextInt(20_000);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (null != connection)
                        connection.send(PROTOCOL_GRID);
                }
            }, intval, intval);
        }

        private void reconnect(final String host, final int port)
        {
            try {

                Thread.sleep(5_000);
            } catch (InterruptedException e)
            {

            }

            connect(host, port);
        }

        private void connect(final String host, final int port)
        {
            try
            {
                connection = table.connect(host, port);

                connection.send(PROTOCOL_GRID);
                connection.setConnectionListener(new Table.IConnectionListener() {

                    @Override
                    public void onConnected() {

                    }

                    @Override
                    public void onDisconnected(Throwable e) {
                        Log.e(TAG, "disconnect to Table.", e);
                        reconnect(host, port);
                    }
                });

                connection.addListener(PROTOCOL_GRID, ResultProto.Output.class, new Table.IListener<ResultProto.Output>() {
                    @Override
                    public void onRead(ResultProto.Output message) {
                        try {
                            if (!message.getData().isEmpty()) {
                                Grid grid = Jsonable.fromJson(Grid.class, message.getData().toByteArray());
                                setGrid(grid);
                            }
                        } catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                });

            } catch (IOException e)
            {
                Log.e(TAG, "can not connect to Table.", e);
                reconnect(host, port);
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

