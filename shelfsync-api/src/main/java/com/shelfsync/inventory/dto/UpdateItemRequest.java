package com.shelfsync.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Partial metadata update. Null fields are left unchanged. Stock is deliberately absent:
 * it can only change through restock/adjust/checkout so that every change is ledgered.
 *
 * @param expectedVersion optional optimistic-lock check against the version the client last saw
 */
public record UpdateItemRequest(
        @Size(min = 1, max = 200) String name,
        @Size(min = 1, max = 80) String category,
        @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @Min(0) Integer reorderLevel,
        Long expectedVersion) {
}
