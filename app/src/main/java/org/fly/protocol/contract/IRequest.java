package org.fly.protocol.contract;

import org.fly.protocol.exception.RequestException;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface IRequest {

    void write(ByteBuffer byteBuffer) throws RequestException, IOException;
}
