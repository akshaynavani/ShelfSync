package com.shelfsync.common;

/** The request is valid but conflicts with the current state (duplicate SKU, stale version, ...). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
