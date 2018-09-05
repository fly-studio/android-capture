package org.fly.protocol.exception;

import android.annotation.TargetApi;

public class PtrException extends Exception {
    public PtrException() {
    }

    public PtrException(String message) {
        super(message);
    }

    public PtrException(String message, Throwable cause) {
        super(message, cause);
    }

    public PtrException(Throwable cause) {
        super(cause);
    }

    @TargetApi(24)
    public PtrException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
