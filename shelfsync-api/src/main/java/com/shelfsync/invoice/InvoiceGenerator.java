package com.shelfsync.invoice;

import com.shelfsync.invoice.core.InvoiceRequest;
import com.shelfsync.invoice.core.InvoiceResult;

/** Produces an invoice PDF and stores it in S3, returning where it landed. */
public interface InvoiceGenerator {

    InvoiceResult generate(InvoiceRequest request);
}
