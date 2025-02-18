package com.shelfsync.inventory;

import com.shelfsync.common.ConflictException;
import com.shelfsync.common.NotFoundException;
import com.shelfsync.config.ShelfSyncProperties;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB access for live inventory state.
 * <p>
 * Metadata writes go through the enhanced client (optimistic locking on {@code version});
 * stock changes go through a low-level conditional {@code UpdateItem} so that concurrent sales
 * are serialised by DynamoDB itself and stock can never drop below zero.
 */
@Repository
public class InventoryRepository {

    static final TableSchema<InventoryItem> SCHEMA = TableSchema.fromBean(InventoryItem.class);

    private final DynamoDbClient client;
    private final DynamoDbTable<InventoryItem> table;
    private final String tableName;
    private final Clock clock;

    public InventoryRepository(DynamoDbClient client, DynamoDbEnhancedClient enhancedClient,
                               ShelfSyncProperties properties, Clock clock) {
        this.client = client;
        this.tableName = properties.dynamodb().inventoryTable();
        this.table = enhancedClient.table(tableName, SCHEMA);
        this.clock = clock;
    }

    public Optional<InventoryItem> findBySku(String sku) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(sku).build()));
    }

    public InventoryItem getBySku(String sku) {
        return findBySku(sku).orElseThrow(() -> NotFoundException.of("Item", sku));
    }

    /** Inserts a new item; fails with {@link ConflictException} if the SKU already exists. */
    public InventoryItem create(InventoryItem item) {
        Instant now = clock.instant();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setVersion(null);
        try {
            table.putItem(PutItemEnhancedRequest.builder(InventoryItem.class)
                    .item(item)
                    .conditionExpression(Expression.builder()
                            .expression("attribute_not_exists(sku)")
                            .build())
                    .build());
        } catch (ConditionalCheckFailedException e) {
            throw new ConflictException("Item already exists: " + item.getSku(), e);
        }
        return item;
    }

    /** Saves metadata changes; the version attribute guards against lost updates. */
    public InventoryItem update(InventoryItem item) {
        item.setUpdatedAt(clock.instant());
        try {
            return table.updateItem(item);
        } catch (ConditionalCheckFailedException e) {
            throw new ConflictException("Item " + item.getSku() + " was modified concurrently; reload and retry", e);
        }
    }

    public void delete(String sku) {
        InventoryItem deleted = table.deleteItem(Key.builder().partitionValue(sku).build());
        if (deleted == null) {
            throw NotFoundException.of("Item", sku);
        }
    }

    /**
     * Atomically adds {@code delta} (may be negative) to the item's stock.
     *
     * @return the item state after the update
     * @throws NotFoundException          if the SKU does not exist
     * @throws InsufficientStockException if the update would make stock negative
     */
    public InventoryItem adjustStock(String sku, int delta) {
        String condition = "attribute_exists(sku)";
        Map<String, AttributeValue> values = new java.util.HashMap<>();
        values.put(":delta", AttributeValue.fromN(Integer.toString(delta)));
        values.put(":one", AttributeValue.fromN("1"));
        values.put(":now", AttributeValue.fromS(clock.instant().toString()));
        if (delta < 0) {
            condition += " AND stock >= :required";
            values.put(":required", AttributeValue.fromN(Integer.toString(-delta)));
        }

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("sku", AttributeValue.fromS(sku)))
                .updateExpression("SET stock = stock + :delta, updatedAt = :now ADD version :one")
                .conditionExpression(condition)
                .expressionAttributeValues(values)
                .returnValues(ReturnValue.ALL_NEW)
                .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                .build();
        try {
            UpdateItemResponse response = client.updateItem(request);
            return SCHEMA.mapToItem(response.attributes());
        } catch (ConditionalCheckFailedException e) {
            if (!e.hasItem() || e.item().isEmpty()) {
                throw NotFoundException.of("Item", sku);
            }
            InventoryItem current = SCHEMA.mapToItem(e.item());
            int available = current.getStock() == null ? 0 : current.getStock();
            throw new InsufficientStockException(sku, -delta, available);
        }
    }

    public List<InventoryItem> findAll() {
        List<InventoryItem> items = new ArrayList<>();
        table.scan().items().forEach(items::add);
        return items;
    }

    public List<InventoryItem> findByCategory(String category) {
        SdkIterable<Page<InventoryItem>> pages = table.index(InventoryItem.CATEGORY_INDEX)
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(category).build()));
        List<InventoryItem> items = new ArrayList<>();
        pages.forEach(page -> items.addAll(page.items()));
        return items;
    }

    /** Items at or below their reorder level. A filtered scan is fine at personal-inventory scale. */
    public List<InventoryItem> findLowStock() {
        ScanEnhancedRequest request = ScanEnhancedRequest.builder()
                .filterExpression(Expression.builder()
                        .expression("attribute_exists(reorderLevel) AND stock <= reorderLevel")
                        .build())
                .build();
        List<InventoryItem> items = new ArrayList<>();
        table.scan(request).items().forEach(items::add);
        return items;
    }
}
