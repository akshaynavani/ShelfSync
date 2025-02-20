package com.shelfsync.billing.dto;

import com.shelfsync.billing.OrderLine;
import com.shelfsync.billing.OrderStatus;
import com.shelfsync.billing.SalesOrder;
import com.shelfsync.invoice.Invoice;
import com.shelfsync.invoice.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderNumber,
        OrderStatus status,
        String customerName,
        String customerEmail,
        String currency,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal tax,
        BigDecimal total,
        Instant createdAt,
        Instant cancelledAt,
        List<LineResponse> lines,
        InvoiceSummary invoice) {

    public record LineResponse(String sku, String itemName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
        static LineResponse from(OrderLine line) {
            return new LineResponse(line.getSku(), line.getItemName(), line.getUnitPrice(),
                    line.getQuantity(), line.getLineTotal());
        }
    }

    public record InvoiceSummary(String invoiceNumber, InvoiceStatus status, Instant generatedAt) {
        static InvoiceSummary from(Invoice invoice) {
            return invoice == null ? null
                    : new InvoiceSummary(invoice.getInvoiceNumber(), invoice.getStatus(), invoice.getGeneratedAt());
        }
    }

    public static OrderResponse from(SalesOrder order, Invoice invoice) {
        return new OrderResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getCurrency(),
                order.getSubtotal(),
                order.getTaxRate(),
                order.getTax(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getCancelledAt(),
                order.getLines().stream().map(LineResponse::from).toList(),
                InvoiceSummary.from(invoice));
    }
}
