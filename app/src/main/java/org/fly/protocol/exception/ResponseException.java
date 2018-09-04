package org.fly.protocol.exception;

import org.fly.protocol.http.response.Status;

public final class ResponseException extends Exception {

    private static final long serialVersionUID = 6569838532917408380L;

    private final Status status;

    public ResponseException(Status status, String message) {
        super(message);
        this.status = status;
    }

    public ResponseException(Status status, String message, Exception e) {
        super(message, e);
        this.status = status;
    }

    public Status getStatus() {
        return this.status;
    }
}
