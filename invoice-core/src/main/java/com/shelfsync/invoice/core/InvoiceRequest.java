package com.shelfsync.invoice.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything needed to render an invoice. Mutable JavaBean (no-arg constructor + setters) so the
 * AWS Lambda Java runtime can deserialise it directly from the invocation payload.
 */
public class InvoiceRequest {

    private String invoiceNumber;
    private String orderNumber;
    /** ISO-8601 instant, e.g. 2026-01-31T10:15:30Z. */
    private String issuedAt;
    private String currency;
    private Party seller = new Party();
    private Party customer = new Party();
    private List<Line> lines = new ArrayList<>();
    private BigDecimal subtotal;
    private BigDecimal taxRate;
    private BigDecimal tax;
    private BigDecimal total;

    public static class Party {
        private String name;
        private String address;
        private String email;

        public Party() {
        }

        public Party(String name, String address, String email) {
            this.name = name;
            this.address = address;
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class Line {
        private String sku;
        private String description;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;

        public Line() {
        }

        public Line(String sku, String description, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
            this.sku = sku;
            this.description = description;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.lineTotal = lineTotal;
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public BigDecimal getLineTotal() {
            return lineTotal;
        }

        public void setLineTotal(BigDecimal lineTotal) {
            this.lineTotal = lineTotal;
        }
    }

    /** Throws {@link IllegalArgumentException} if mandatory fields are missing. */
    public void validate() {
        require(invoiceNumber, "invoiceNumber");
        require(issuedAt, "issuedAt");
        require(currency, "currency");
        require(total, "total");
        if (customer == null || customer.getName() == null) {
            throw new IllegalArgumentException("customer.name is required");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("at least one line is required");
        }
        if (!invoiceNumber.matches("[A-Za-z0-9-]{1,32}")) {
            throw new IllegalArgumentException("invoiceNumber has invalid characters");
        }
    }

    private static void require(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(String issuedAt) {
        this.issuedAt = issuedAt;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Party getSeller() {
        return seller;
    }

    public void setSeller(Party seller) {
        this.seller = seller;
    }

    public Party getCustomer() {
        return customer;
    }

    public void setCustomer(Party customer) {
        this.customer = customer;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }
}
