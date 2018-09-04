package org.fly.android.localvpn.firewall;

import org.fly.android.localvpn.contract.IFirewall;
import org.fly.protocol.exception.RequestException;
import org.fly.protocol.exception.ResponseException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class Dns implements IFirewall {

    private Firewall firewall;

    public Dns(Firewall firewall) {
        this.firewall = firewall;
    }

    public static boolean maybe(ByteBuffer byteBuffer)
    {
        return false;
    }

    @Override
    public LinkedList<ByteBuffer> write(ByteBuffer byteBuffer) throws IOException, RequestException, ResponseException {

        return null;
    }
}
