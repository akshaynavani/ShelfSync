package com.shelfsync.invoice;

public enum InvoiceStatus {
    /** Created at checkout, PDF not produced yet. */
    PENDING,
    /** PDF stored in S3. */
    GENERATED,
    /** Last generation attempt failed; the retry job will pick it up until max attempts. */
    FAILED,
    /** Order was cancelled before or after generation. */
    VOID
}
