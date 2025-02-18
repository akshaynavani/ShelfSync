package com.shelfsync.inventory.dto;

import com.shelfsync.inventory.InventoryItem;

import java.math.BigDecimal;
import java.time.Instant;

public record ItemResponse(
        String sku,
        String name,
        String category,
        BigDecimal unitPrice,
        int stock,
        Integer reorderLevel,
        boolean lowStock,
        Long version,
        Instant createdAt,
        Instant updatedAt) {

    public static ItemResponse from(InventoryItem item) {
        return new ItemResponse(
                item.getSku(),
                item.getName(),
                item.getCategory(),
                item.getUnitPrice(),
                item.getStock() == null ? 0 : item.getStock(),
                item.getReorderLevel(),
                item.isLowStock(),
                item.getVersion(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
