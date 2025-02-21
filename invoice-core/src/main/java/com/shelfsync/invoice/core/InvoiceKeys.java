package com.shelfsync.invoice.core;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** S3 object key layout for invoices: {@code invoices/yyyy/MM/<invoiceNumber>.pdf}. */
public final class InvoiceKeys {

    private InvoiceKeys() {
    }

    public static String keyFor(InvoiceRequest request) {
        ZonedDateTime issued = Instant.parse(request.getIssuedAt()).atZone(ZoneOffset.UTC);
        return String.format("invoices/%04d/%02d/%s.pdf",
                issued.getYear(), issued.getMonthValue(), request.getInvoiceNumber());
    }
}
