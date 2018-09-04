package org.fly.android.localvpn.contract;

import org.fly.protocol.exception.RequestException;
import org.fly.protocol.exception.ResponseException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public interface IFirewall {
    LinkedList<ByteBuffer> write(ByteBuffer byteBuffer) throws IOException, RequestException, ResponseException;
}
