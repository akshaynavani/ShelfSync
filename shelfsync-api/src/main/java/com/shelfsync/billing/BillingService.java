package com.shelfsync.billing;

import com.shelfsync.billing.dto.CheckoutRequest;
import com.shelfsync.common.NotFoundException;
import com.shelfsync.config.ShelfSyncProperties;
import com.shelfsync.inventory.InventoryItem;
import com.shelfsync.inventory.InventoryRepository;
import com.shelfsync.invoice.Invoice;
import com.shelfsync.invoice.InvoiceRepository;
import com.shelfsync.ledger.LedgerService;
import com.shelfsync.ledger.MovementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checkout saga across DynamoDB (live stock) and MySQL (orders + ledger):
 * <ol>
 *   <li>Reserve stock for each line with a conditional decrement in DynamoDB.</li>
 *   <li>Persist the order, its lines, SALE ledger rows and a PENDING invoice in one MySQL transaction.</li>
 *   <li>If anything fails, release every reservation made so far (compensating increments).</li>
 * </ol>
 * After commit an {@link OrderPlacedEvent} triggers asynchronous invoice generation.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final InventoryRepository inventory;
    private final SalesOrderRepository orders;
    private final InvoiceRepository invoices;
    private final LedgerService ledger;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final ShelfSyncProperties.Billing billing;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public BillingService(InventoryRepository inventory, SalesOrderRepository orders, InvoiceRepository invoices,
                          LedgerService ledger, TransactionTemplate tx, ApplicationEventPublisher events,
                          ShelfSyncProperties properties, Clock clock) {
        this.inventory = inventory;
        this.orders = orders;
        this.invoices = invoices;
        this.ledger = ledger;
        this.tx = tx;
        this.events = events;
        this.billing = properties.billing();
        this.clock = clock;
    }

    record Reservation(String sku, int quantity, InventoryItem itemAfter) {
    }

    public record CheckoutResult(SalesOrder order, Invoice invoice, boolean replayed) {
    }

    public CheckoutResult checkout(CheckoutRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = orders.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return replay(existing.get());
            }
        }

        Map<String, Integer> quantities = mergeLines(request.lines());
        String orderNumber = newOrderNumber();
        List<Reservation> reserved = new ArrayList<>();
        try {
            for (Map.Entry<String, Integer> line : quantities.entrySet()) {
                InventoryItem after = inventory.adjustStock(line.getKey(), -line.getValue());
                reserved.add(new Reservation(line.getKey(), line.getValue(), after));
            }
            CheckoutResult result = tx.execute(status -> persist(request, idempotencyKey, orderNumber, reserved));
            log.info("Order {} placed: {} line(s), total {} {}", orderNumber, reserved.size(),
                    result.order().getTotal(), result.order().getCurrency());
            return result;
        } catch (RuntimeException e) {
            release(reserved, orderNumber);
            if (e instanceof DataIntegrityViolationException && idempotencyKey != null) {
                // A concurrent request with the same key won the race; return its result.
                var winner = orders.findByIdempotencyKey(idempotencyKey);
                if (winner.isPresent()) {
                    return replay(winner.get());
                }
            }
            throw e;
        }
    }

    private CheckoutResult persist(CheckoutRequest request, String idempotencyKey, String orderNumber,
                                   List<Reservation> reserved) {
        Instant now = clock.instant();
        SalesOrder order = new SalesOrder(orderNumber, idempotencyKey, request.customerName().trim(),
                request.customerEmail(), billing.currency(), billing.taxRate(), now);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Reservation r : reserved) {
            InventoryItem item = r.itemAfter();
            BigDecimal lineTotal = Money.lineTotal(item.getUnitPrice(), r.quantity());
            order.addLine(new OrderLine(item.getSku(), item.getName(), item.getUnitPrice(), r.quantity(), lineTotal));
            subtotal = subtotal.add(lineTotal);
        }
        BigDecimal tax = Money.tax(subtotal, billing.taxRate());
        order.applyTotals(Money.round(subtotal), tax, Money.round(subtotal.add(tax)));
        order = orders.saveAndFlush(order);

        for (Reservation r : reserved) {
            ledger.record(r.sku(), MovementType.SALE, -r.quantity(), r.itemAfter().getStock(), orderNumber, null);
        }

        Invoice invoice = invoices.save(new Invoice(order, invoiceNumberFor(order), now));
        events.publishEvent(new OrderPlacedEvent(order.getId(), invoice.getId(), orderNumber));
        return new CheckoutResult(order, invoice, false);
    }

    private CheckoutResult replay(SalesOrder order) {
        return new CheckoutResult(order, invoices.findByOrderId(order.getId()).orElse(null), true);
    }

    /** Best-effort compensation: give reserved stock back. Failures are logged for reconciliation. */
    private void release(List<Reservation> reserved, String orderNumber) {
        for (Reservation r : reserved) {
            try {
                inventory.adjustStock(r.sku(), r.quantity());
            } catch (RuntimeException e) {
                log.error("Failed to release {} x {} for aborted order {}; manual reconciliation needed",
                        r.quantity(), r.sku(), orderNumber, e);
            }
        }
        if (!reserved.isEmpty()) {
            log.warn("Checkout {} aborted; released {} reservation(s)", orderNumber, reserved.size());
        }
    }

    /** Cancels an order, voids its invoice and returns its stock (ledgered as SALE_REVERSAL). */
    public CheckoutResult cancel(String orderNumber) {
        CheckoutResult cancelled = tx.execute(status -> {
            SalesOrder order = orders.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> NotFoundException.of("Order", orderNumber));
            order.cancel(clock.instant());
            Invoice invoice = invoices.findByOrderId(order.getId()).orElse(null);
            if (invoice != null) {
                invoice.markVoid();
            }
            return new CheckoutResult(order, invoice, false);
        });
        for (OrderLine line : cancelled.order().getLines()) {
            InventoryItem after = inventory.adjustStock(line.getSku(), line.getQuantity());
            ledger.record(line.getSku(), MovementType.SALE_REVERSAL, line.getQuantity(), after.getStock(),
                    orderNumber, "Order cancelled");
        }
        log.info("Order {} cancelled; stock returned for {} line(s)", orderNumber, cancelled.order().getLines().size());
        return cancelled;
    }

    public CheckoutResult get(String orderNumber) {
        SalesOrder order = orders.findByOrderNumber(orderNumber)
                .orElseThrow(() -> NotFoundException.of("Order", orderNumber));
        return replay(order);
    }

    public Page<SalesOrder> list(int page, int size) {
        return orders.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    static Map<String, Integer> mergeLines(List<CheckoutRequest.Line> lines) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (CheckoutRequest.Line line : lines) {
            merged.merge(line.sku().trim(), line.quantity(), Integer::sum);
        }
        return merged;
    }

    private String newOrderNumber() {
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return "SO-" + DAY.format(clock.instant()) + "-" + suffix;
    }

    static String invoiceNumberFor(SalesOrder order) {
        return "INV-" + DAY.format(order.getCreatedAt()) + "-" + String.format("%06d", order.getId());
    }
}
