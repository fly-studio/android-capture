package com.fly.android.localvpn;

import android.util.Log;

import com.fly.protocol.Protocol;
import com.fly.protocol.cache.ByteBufferPool;
import com.fly.protocol.exception.RequestException;
import com.fly.protocol.exception.ResponseException;
import com.fly.protocol.http.request.Request;
import com.fly.protocol.http.response.Response;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedList;

public class Firewall {

    private static final String TAG = Firewall.class.getSimpleName();

    private TCB tcb;

    private enum Status {
        ACCEPT, // 放行
        DROP, // 丢包
        INCOMPLETE, //包不完整
    }

    private LinkedList<ByteBuffer> cache = new LinkedList<>();
    private LinkedList<ByteBuffer> response = new LinkedList<>();

    private Status status = Status.INCOMPLETE;
    private Request httpRequest = null;
    private Protocol protocol = Protocol.NONE;
    private long count = 0;

    public Firewall(TCB tcb) {

        this.tcb = tcb;
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
        {
            buffer.put(byteBuffer);
        }

        buffer.flip();

        cache.add(buffer.duplicate());

        update(buffer.duplicate());
    }

    private String cacheToString()
    {
        StringBuilder stringBuilder = new StringBuilder();
        for (ByteBuffer buffer : cache
                )
            stringBuilder.append(StandardCharsets.US_ASCII.decode(buffer.duplicate()));

        return stringBuilder.toString();
    }

    private void update(ByteBuffer buffer) {

        // 第一个包就可以判断出是什么协议
        // 对于MTU短到 GET / 都无法一个包的场景, 就放行吧
        if (protocol == Protocol.NONE)
        {
            // http or not
            protocol = !Request.maybe(StandardCharsets.US_ASCII.decode(buffer.duplicate()).toString()) ? Protocol.OTHER : Protocol.HTTP;
        }

        // 目前只对http协议进行分析
        if (protocol != Protocol.HTTP) {
            status = Status.ACCEPT;
            return;
        }

        try
        {
            // 可能是HTTP
            if (null == httpRequest)
                httpRequest = new Request();

            // 依次写入
            httpRequest.write(buffer.duplicate());

            boolean hijack = false;

            if (httpRequest.isHeaderComplete())
            {
                String url = httpRequest.getUrl();

                if (url.matches(".*?(xtool\\.23ox\\.cn/api/xtools/x008/status).*?"))
                {
                    hijack = true;
                    status = Status.DROP;
                } else
                    status = Status.ACCEPT;
            }

            // 包体结束, 清除httpRequest等待通道复用
            if (httpRequest.isBodyComplete())
            {
                String url = httpRequest.getUrl();

                Log.d(TAG, httpRequest.getMethod() + ": " + url);

                if (hijack)
                {

                    String id = httpRequest.input("id");

                    if (id != null && !id.isEmpty())
                    {
                        Response res = Response.newFixedLengthResponse("{" +
                                "\"id\": \"" + id + "\"," +
                                "\"time\": \"" + (new Date().getTime() + 200 * 365 *  86400_000L) + "\"," +
                                "\"userName\": \"" + id + "\"," +
                                "}");
                        ByteBuffer byteBuffer = ByteBufferPool.acquire();
                        res.send(byteBuffer);

                        response.add(byteBuffer);
                    }


                }

                httpRequest = null;
            }

        }
        catch (IOException | RequestException | ResponseException e)
        {
            protocol = Protocol.OTHER;
            status = Status.ACCEPT;

            httpRequest = null;

            Log.e(TAG,  e.getMessage(), e);
        }

    }
}

