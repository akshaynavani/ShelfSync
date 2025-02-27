package com.shelfsync.invoice;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    public record InvoiceResponse(String invoiceNumber, InvoiceStatus status, int attempts, String lastError,
                                  Instant createdAt, Instant generatedAt, String downloadUrl) {
    }

    @GetMapping("/{invoiceNumber}")
    public InvoiceResponse get(@PathVariable String invoiceNumber) {
        return toResponse(service.getByNumber(invoiceNumber));
    }

    /** Redirects to a short-lived presigned S3 URL for the PDF. */
    @GetMapping("/{invoiceNumber}/pdf")
    public ResponseEntity<Void> download(@PathVariable String invoiceNumber) {
        Invoice invoice = service.getByNumber(invoiceNumber);
        URI target = URI.create(service.downloadUrl(invoice).toString());
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target.toString()).build();
    }

    @PostMapping("/{invoiceNumber}/regenerate")
    public InvoiceResponse regenerate(@PathVariable String invoiceNumber) {
        return toResponse(service.regenerate(invoiceNumber));
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        String url = invoice.getStatus() == InvoiceStatus.GENERATED ? service.downloadUrl(invoice).toString() : null;
        return new InvoiceResponse(invoice.getInvoiceNumber(), invoice.getStatus(), invoice.getAttempts(),
                invoice.getLastError(), invoice.getCreatedAt(), invoice.getGeneratedAt(), url);
    }
}
