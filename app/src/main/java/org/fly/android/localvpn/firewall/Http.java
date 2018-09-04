package org.fly.android.localvpn.firewall;

import android.util.Log;

import org.fly.android.localvpn.contract.IFirewall;
import org.fly.protocol.cache.ByteBufferPool;
import org.fly.protocol.exception.RequestException;
import org.fly.protocol.exception.ResponseException;
import org.fly.protocol.http.request.Request;
import org.fly.protocol.http.response.Response;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedList;

public class Http implements IFirewall {

    private static final String TAG = Http.class.getSimpleName();

    private Request request = null;

    private Firewall firewall;

    public Http(Firewall firewall) {
        this.firewall = firewall;
    }

    public static boolean maybe(ByteBuffer byteBuffer)
    {
        return Request.maybe(StandardCharsets.US_ASCII.decode(byteBuffer.duplicate()).toString());
    }

    @Override
    public LinkedList<ByteBuffer> write(ByteBuffer byteBuffer) throws IOException, RequestException, ResponseException {
        if (null == request)
            request = new Request();

        LinkedList<ByteBuffer> results = new LinkedList<>();

        request.write(byteBuffer.duplicate());

        // 依次写入

        boolean hijack = false;

        if (request.isHeaderComplete())
        {
            String url = request.getUrl();

            if (url.matches(".*?(xtool\\.23ox\\.cn/api/xtools/x008/status).*?"))
            {
                hijack = true;
                firewall.drop();
            } else
                firewall.accept();
        }

        // 包体结束, 清除httpRequest等待通道复用
        if (request.isBodyComplete())
        {
            String url = request.getUrl();

            Log.d(TAG, request.getMethod() + ": " + url);

            if (hijack)
            {
                String id = request.input("id");

                if (id != null && !id.isEmpty())
                {
                    Response response = Response.newFixedLengthResponse("{" +
                            "\"id\": \"" + id + "\"," +
                            "\"time\": \"" + (new Date().getTime() + 200 * 365 *  86400_000L) + "\"," +
                            "\"userName\": \"" + id + "\"," +
                            "}");
                    ByteBuffer buffer = ByteBufferPool.acquire();
                    response.send(buffer);

                    results.add(byteBuffer);
                }

            }

            request = null;
        }

        return results;
    }
}
