package com.shelfsync.invoice;

import com.shelfsync.billing.OrderLine;
import com.shelfsync.billing.OrderPlacedEvent;
import com.shelfsync.billing.SalesOrder;
import com.shelfsync.common.NotFoundException;
import com.shelfsync.config.ShelfSyncProperties;
import com.shelfsync.invoice.core.InvoiceRequest;
import com.shelfsync.invoice.core.InvoiceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

/**
 * Turns PENDING invoices into PDFs in S3.
 * <p>
 * Generation runs after the checkout transaction commits, on the {@code invoiceExecutor} pool, so
 * checkout latency never includes Lambda/PDF time. A scheduled sweep retries anything that failed
 * or was missed (e.g. the app restarted between commit and generation).
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    /** Pending invoices younger than this are assumed to be in flight via the event listener. */
    private static final Duration PENDING_GRACE = Duration.ofMinutes(2);

    private final InvoiceRepository invoices;
    private final InvoiceGenerator generator;
    private final S3Presigner presigner;
    private final TransactionTemplate tx;
    private final ShelfSyncProperties properties;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, InvoiceGenerator generator, S3Presigner presigner,
                          TransactionTemplate tx, ShelfSyncProperties properties, Clock clock) {
        this.invoices = invoices;
        this.generator = generator;
        this.presigner = presigner;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
    }

    @Async("invoiceExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.debug("Generating invoice for order {}", event.orderNumber());
        generate(event.invoiceId());
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${shelfsync.invoice.retry-interval:PT5M}")
    public void retryOutstanding() {
        List<Long> ids = invoices.findRetryCandidates(
                EnumSet.of(InvoiceStatus.PENDING, InvoiceStatus.FAILED),
                properties.invoice().maxAttempts(),
                clock.instant().minus(PENDING_GRACE));
        if (!ids.isEmpty()) {
            log.info("Retrying generation for {} invoice(s)", ids.size());
        }
        ids.forEach(this::generate);
    }

    /**
     * Generates (or regenerates) the PDF for one invoice. Never throws for generation failures;
     * the outcome is recorded on the invoice row instead.
     *
     * @return the invoice state after the attempt
     */
    public Invoice generate(Long invoiceId) {
        InvoiceRequest request = tx.execute(status -> {
            Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> NotFoundException.of("Invoice", invoiceId));
            if (invoice.getStatus() == InvoiceStatus.GENERATED || invoice.getStatus() == InvoiceStatus.VOID) {
                return null;
            }
            return toRequest(invoice);
        });
        if (request == null) {
            return invoices.findById(invoiceId).orElseThrow();
        }

        InvoiceResult result = null;
        String error = null;
        try {
            result = generator.generate(request);
        } catch (RuntimeException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Invoice {} generation failed: {}", request.getInvoiceNumber(), error);
        }

        final InvoiceResult outcome = result;
        final String failure = error;
        try {
            return tx.execute(status -> {
                Invoice invoice = invoices.findById(invoiceId).orElseThrow();
                if (invoice.getStatus() == InvoiceStatus.VOID) {
                    return invoice;
                }
                if (outcome != null) {
                    invoice.markGenerated(outcome.getKey(), clock.instant());
                    log.info("Invoice {} stored at s3://{}/{}", invoice.getInvoiceNumber(),
                            outcome.getBucket(), outcome.getKey());
                } else {
                    invoice.markFailed(failure);
                }
                return invoices.save(invoice);
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Invoice {} was updated concurrently; keeping the other writer's result", invoiceId);
            return invoices.findById(invoiceId).orElseThrow();
        }
    }

    public Invoice regenerate(String invoiceNumber) {
        Invoice invoice = getByNumber(invoiceNumber);
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new IllegalStateException("Invoice " + invoiceNumber + " is void");
        }
        if (invoice.getStatus() == InvoiceStatus.GENERATED) {
            // Force a fresh render, e.g. after changing seller details.
            tx.executeWithoutResult(status -> invoices.findById(invoice.getId()).ifPresent(i -> {
                i.markFailed("regeneration requested");
                invoices.save(i);
            }));
        }
        return generate(invoice.getId());
    }

    public Invoice getByNumber(String invoiceNumber) {
        return invoices.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> NotFoundException.of("Invoice", invoiceNumber));
    }

    /** Short-lived presigned GET URL for the invoice PDF. */
    public URL downloadUrl(Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.GENERATED || invoice.getS3Key() == null) {
            throw new IllegalStateException("Invoice " + invoice.getInvoiceNumber() + " has no PDF yet (status "
                    + invoice.getStatus() + ")");
        }
        Duration ttl = properties.s3().presignTtl() == null ? Duration.ofMinutes(15) : properties.s3().presignTtl();
        GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(properties.s3().invoiceBucket())
                        .key(invoice.getS3Key())
                        .build())
                .build();
        return presigner.presignGetObject(presign).url();
    }

    InvoiceRequest toRequest(Invoice invoice) {
        SalesOrder order = invoice.getOrder();
        ShelfSyncProperties.Billing billing = properties.billing();

        InvoiceRequest request = new InvoiceRequest();
        request.setInvoiceNumber(invoice.getInvoiceNumber());
        request.setOrderNumber(order.getOrderNumber());
        request.setIssuedAt(invoice.getCreatedAt().toString());
        request.setCurrency(order.getCurrency());
        request.setSeller(new InvoiceRequest.Party(billing.sellerName(), billing.sellerAddress(), null));
        request.setCustomer(new InvoiceRequest.Party(order.getCustomerName(), null, order.getCustomerEmail()));
        for (OrderLine line : order.getLines()) {
            request.getLines().add(new InvoiceRequest.Line(line.getSku(), line.getItemName(), line.getQuantity(),
                    line.getUnitPrice(), line.getLineTotal()));
        }
        request.setSubtotal(order.getSubtotal());
        request.setTaxRate(order.getTaxRate());
        request.setTax(order.getTax());
        request.setTotal(order.getTotal());
        return request;
    }
}
