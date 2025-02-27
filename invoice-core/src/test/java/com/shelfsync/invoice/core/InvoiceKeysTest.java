package com.shelfsync.invoice.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceKeysTest {

    @Test
    void partitionsKeysByYearAndMonth() {
        InvoiceRequest r = InvoicePdfRendererTest.sample();

        assertThat(InvoiceKeys.keyFor(r)).isEqualTo("invoices/2026/01/INV-20260105-000042.pdf");
    }
}
