package com.shelfsync.invoice.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoicePdfRendererTest {

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

    static InvoiceRequest sample() {
        InvoiceRequest r = new InvoiceRequest();
        r.setInvoiceNumber("INV-20260105-000042");
        r.setOrderNumber("SO-20260105-ABC123");
        r.setIssuedAt("2026-01-05T09:30:00Z");
        r.setCurrency("USD");
        r.setSeller(new InvoiceRequest.Party("ShelfSync Store", "1 Main St", null));
        r.setCustomer(new InvoiceRequest.Party("Ada Lovelace", null, "ada@example.com"));
        r.getLines().add(new InvoiceRequest.Line("MUG-01", "Ceramic mug", 2, new BigDecimal("7.50"), new BigDecimal("15.00")));
        r.getLines().add(new InvoiceRequest.Line("TEA-GRN", "Green tea 100g", 1, new BigDecimal("4.25"), new BigDecimal("4.25")));
        r.setSubtotal(new BigDecimal("19.25"));
        r.setTaxRate(new BigDecimal("0.08"));
        r.setTax(new BigDecimal("1.54"));
        r.setTotal(new BigDecimal("20.79"));
        return r;
    }

    @Test
    void rendersAValidPdf() {
        byte[] pdf = renderer.render(sample());

        assertThat(pdf.length).isGreaterThan(500);
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void rejectsInvoiceWithoutLines() {
        InvoiceRequest r = sample();
        r.getLines().clear();

        assertThatThrownBy(() -> renderer.render(r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line");
    }

    @Test
    void rejectsUnsafeInvoiceNumber() {
        InvoiceRequest r = sample();
        r.setInvoiceNumber("../../etc/passwd");

        assertThatThrownBy(() -> renderer.render(r)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatsMoneyWithCurrencySymbol() {
        assertThat(InvoicePdfRenderer.money(new BigDecimal("3.456"), "USD")).isEqualTo("$3.46");
        assertThat(InvoicePdfRenderer.money(new BigDecimal("10"), "XYZ")).isEqualTo("XYZ 10.00");
    }
}
