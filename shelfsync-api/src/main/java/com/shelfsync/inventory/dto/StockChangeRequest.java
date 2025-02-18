package com.shelfsync.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for restock (quantity must be positive) and manual adjustment (quantity may be negative,
 * e.g. breakage or a stock-take correction).
 */
public record StockChangeRequest(@NotNull Integer quantity, @Size(max = 255) String note) {
}
