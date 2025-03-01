package com.shelfsync;

import com.shelfsync.config.ShelfSyncProperties;
import com.shelfsync.inventory.InventoryItem;

import java.math.BigDecimal;
import java.time.Duration;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static ShelfSyncProperties properties() {
        return new ShelfSyncProperties(
                new ShelfSyncProperties.Security(""),
                new ShelfSyncProperties.Aws("us-east-1", ""),
                new ShelfSyncProperties.DynamoDb("shelfsync-inventory"),
                new ShelfSyncProperties.S3("shelfsync-invoices", Duration.ofMinutes(15)),
                new ShelfSyncProperties.Billing("USD", new BigDecimal("0.08"), "Test Store", null),
                new ShelfSyncProperties.Invoice("local", null, Duration.ofMinutes(5), 5));
    }

    public static InventoryItem item(String sku, String price, int stock) {
        InventoryItem item = new InventoryItem();
        item.setSku(sku);
        item.setName("Item " + sku);
        item.setCategory("general");
        item.setUnitPrice(new BigDecimal(price));
        item.setStock(stock);
        item.setReorderLevel(2);
        item.setVersion(1L);
        return item;
    }
}
