package com.shelfsync.invoice;

public class InvoiceGenerationException extends RuntimeException {

    public InvoiceGenerationException(String message) {
        super(message);
    }

    public InvoiceGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
