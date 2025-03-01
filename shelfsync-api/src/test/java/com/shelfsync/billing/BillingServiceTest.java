package com.shelfsync.billing;

import com.shelfsync.TestFixtures;
import com.shelfsync.billing.dto.CheckoutRequest;
import com.shelfsync.inventory.InsufficientStockException;
import com.shelfsync.inventory.InventoryRepository;
import com.shelfsync.invoice.Invoice;
import com.shelfsync.invoice.InvoiceRepository;
import com.shelfsync.invoice.InvoiceStatus;
import com.shelfsync.ledger.LedgerService;
import com.shelfsync.ledger.MovementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingServiceTest {

    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final SalesOrderRepository orders = mock(SalesOrderRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-02-10T08:00:00Z"), ZoneOffset.UTC);

    private BillingService service;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new BillingService(inventory, orders, invoices, ledger, tx, events, TestFixtures.properties(), clock);

        when(orders.saveAndFlush(any(SalesOrder.class))).thenAnswer(inv -> {
            SalesOrder o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 42L);
            return o;
        });
        when(invoices.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            ReflectionTestUtils.setField(i, "id", 7L);
            return i;
        });
    }

    private static CheckoutRequest request(CheckoutRequest.Line... lines) {
        return new CheckoutRequest("Ada", "ada@example.com", List.of(lines));
    }

    @Test
    void checkoutReservesStockComputesTotalsAndPublishesEvent() {
        when(inventory.adjustStock("MUG", -2)).thenReturn(TestFixtures.item("MUG", "7.50", 8));
        when(inventory.adjustStock("TEA", -1)).thenReturn(TestFixtures.item("TEA", "4.25", 3));

        BillingService.CheckoutResult result = service.checkout(
                request(new CheckoutRequest.Line("MUG", 2), new CheckoutRequest.Line("TEA", 1)), null);

        SalesOrder order = result.order();
        assertThat(order.getSubtotal()).isEqualByComparingTo("19.25");
        assertThat(order.getTax()).isEqualByComparingTo("1.54");
        assertThat(order.getTotal()).isEqualByComparingTo("20.79");
        assertThat(order.getLines()).hasSize(2);
        assertThat(result.invoice().getStatus()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(result.invoice().getInvoiceNumber()).isEqualTo("INV-20260210-000042");

        verify(ledger).record(eq("MUG"), eq(MovementType.SALE), eq(-2), eq(8), eq(order.getOrderNumber()), isNull());
        verify(ledger).record(eq("TEA"), eq(MovementType.SALE), eq(-1), eq(3), eq(order.getOrderNumber()), isNull());
        verify(events).publishEvent(new OrderPlacedEvent(42L, 7L, order.getOrderNumber()));
    }

    @Test
    void duplicateSkusAreMergedIntoOneReservation() {
        when(inventory.adjustStock("MUG", -5)).thenReturn(TestFixtures.item("MUG", "1.00", 0));

        BillingService.CheckoutResult result = service.checkout(
                request(new CheckoutRequest.Line("MUG", 2), new CheckoutRequest.Line("MUG", 3)), null);

        assertThat(result.order().getLines()).singleElement()
                .satisfies(line -> assertThat(line.getQuantity()).isEqualTo(5));
    }

    @Test
    void insufficientStockReleasesEarlierReservations() {
        when(inventory.adjustStock("MUG", -2)).thenReturn(TestFixtures.item("MUG", "7.50", 8));
        when(inventory.adjustStock("TEA", -9)).thenThrow(new InsufficientStockException("TEA", 9, 3));

        assertThatThrownBy(() -> service.checkout(
                request(new CheckoutRequest.Line("MUG", 2), new CheckoutRequest.Line("TEA", 9)), null))
                .isInstanceOf(InsufficientStockException.class);

        verify(inventory).adjustStock("MUG", 2);
        verify(orders, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void databaseFailureReleasesAllReservations() {
        when(inventory.adjustStock("MUG", -1)).thenReturn(TestFixtures.item("MUG", "7.50", 9));
        when(inventory.adjustStock("TEA", -1)).thenReturn(TestFixtures.item("TEA", "4.25", 3));
        when(ledger.record(anyString(), any(), any(Integer.class), any(Integer.class), any(), any()))
                .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.checkout(
                request(new CheckoutRequest.Line("MUG", 1), new CheckoutRequest.Line("TEA", 1)), null))
                .hasMessage("db down");

        verify(inventory).adjustStock("MUG", 1);
        verify(inventory).adjustStock("TEA", 1);
    }

    @Test
    void repeatedIdempotencyKeyReturnsOriginalOrderWithoutTouchingStock() {
        SalesOrder existing = new SalesOrder("SO-1", "key-1", "Ada", null, "USD", new BigDecimal("0.08"), clock.instant());
        ReflectionTestUtils.setField(existing, "id", 5L);
        when(orders.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
        when(invoices.findByOrderId(5L)).thenReturn(Optional.empty());

        BillingService.CheckoutResult result = service.checkout(request(new CheckoutRequest.Line("MUG", 1)), "key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.order()).isSameAs(existing);
        verify(inventory, never()).adjustStock(anyString(), any(Integer.class));
    }
}
