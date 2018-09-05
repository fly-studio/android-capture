package org.fly.android.localvpn.firewall;

import org.fly.android.localvpn.contract.IFirewall;
import org.fly.protocol.cache.ByteBufferPool;
import org.fly.protocol.dns.request.Request;
import org.fly.protocol.dns.response.Response;
import org.fly.protocol.exception.RequestException;
import org.fly.protocol.exception.ResponseException;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class Dns implements IFirewall {

    private Firewall firewall;

    public Dns(Firewall firewall) {
        this.firewall = firewall;
    }

    private static Request getRequest(ByteBuffer byteBuffer)
    {
        Request request = new Request();

        try {
            request.read(byteBuffer);
        } catch (Exception e)
        {
            return null;
        }

        return request;
    }

    public static boolean maybe(ByteBuffer byteBuffer)
    {
        Request.Header header = new Request.Header();

        try
        {
            header.read(byteBuffer.duplicate());

            if (!header.isQuery() || !header.isQueryDomain() || header.getQdCount() != 1)
            {
                return false;
            }

        } catch (BufferUnderflowException e)
        {
            return false;
        }

        return getRequest(byteBuffer.duplicate()) != null;
    }

    @Override
    public LinkedList<ByteBuffer> write(ByteBuffer byteBuffer) throws IOException, RequestException, ResponseException {

        Request request = getRequest(byteBuffer);

        if (request == null || request.getHeader().getQdCount() <= 0)
        {
            System.out.println("DNS?????????????????");
        }

        Request.Query record = request.getQuestions().get(0);

        System.out.println("DNS: -- " + record.getName() + "-" + record.getType());

        if (record.isA() && record.getName().equalsIgnoreCase("api2.apk008.com"))
        {

            LinkedList<ByteBuffer> linkedList = new LinkedList<>();

            try {
                ByteBuffer out = ByteBufferPool.acquire();
                Response response = Response.create(request.getHeader().getId(), record.getName(), "192.168.1.144", 10);
                if (request.getHeader().getRd() > 0)
                {
                    response.getHeader().setRa(1);
                    response.getHeader().setRd(1);
                }

                response.write(out);
                linkedList.add(out);

                firewall.drop();

            } catch (Exception e)
            {
                e.printStackTrace();
                firewall.accept();
                return null;
            }

            return linkedList;
        }

        firewall.accept();

        return null;
    }
}
