package com.shelfsync.invoice;

import com.shelfsync.billing.SalesOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "invoices")
public class Invoice {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private SalesOrder order;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 32)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "s3_key", length = 512)
    private String s3Key;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Version
    private long version;

    protected Invoice() {
    }

    public Invoice(SalesOrder order, String invoiceNumber, Instant createdAt) {
        this.order = order;
        this.invoiceNumber = invoiceNumber;
        this.createdAt = createdAt;
        this.status = InvoiceStatus.PENDING;
    }

    public void markGenerated(String s3Key, Instant when) {
        this.attempts++;
        this.status = InvoiceStatus.GENERATED;
        this.s3Key = s3Key;
        this.generatedAt = when;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.attempts++;
        this.status = InvoiceStatus.FAILED;
        this.lastError = error == null ? "unknown error"
                : error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }

    public void markVoid() {
        this.status = InvoiceStatus.VOID;
    }

    public Long getId() {
        return id;
    }

    public SalesOrder getOrder() {
        return order;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public String getS3Key() {
        return s3Key;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public long getVersion() {
        return version;
    }
}
