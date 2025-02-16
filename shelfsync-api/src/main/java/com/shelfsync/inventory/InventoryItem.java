package com.shelfsync.inventory;

import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Live state of a single stock-keeping unit, stored in DynamoDB.
 * <p>
 * {@code stock} is only ever changed through atomic update expressions (see
 * {@link InventoryRepository#adjustStock}); {@code version} provides optimistic locking for
 * metadata edits made through the enhanced client.
 */
@DynamoDbBean
public class InventoryItem {

    public static final String CATEGORY_INDEX = "category-index";

    private String sku;
    private String name;
    private String category;
    private BigDecimal unitPrice;
    private Integer stock;
    private Integer reorderLevel;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    @DynamoDbPartitionKey
    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = CATEGORY_INDEX)
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(Integer reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    @DynamoDbVersionAttribute
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isLowStock() {
        return stock != null && reorderLevel != null && stock <= reorderLevel;
    }
}
