package com.shelfsync.inventory;

import com.shelfsync.common.ConflictException;
import com.shelfsync.inventory.dto.CreateItemRequest;
import com.shelfsync.inventory.dto.UpdateItemRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    public InventoryItem create(CreateItemRequest req) {
        InventoryItem item = new InventoryItem();
        item.setSku(req.sku().trim());
        item.setName(req.name().trim());
        item.setCategory(req.category().trim().toLowerCase());
        item.setUnitPrice(req.unitPrice());
        item.setStock(req.initialStock());
        item.setReorderLevel(req.reorderLevel());
        InventoryItem created = repository.create(item);
        log.info("Created item {} with initial stock {}", created.getSku(), created.getStock());
        return created;
    }

    public InventoryItem get(String sku) {
        return repository.getBySku(sku);
    }

    public List<InventoryItem> list(String category) {
        List<InventoryItem> items = (category == null || category.isBlank())
                ? repository.findAll()
                : repository.findByCategory(category.trim().toLowerCase());
        return items.stream().sorted(Comparator.comparing(InventoryItem::getSku)).toList();
    }

    public List<InventoryItem> lowStock() {
        return repository.findLowStock().stream()
                .sorted(Comparator.comparing(InventoryItem::getStock))
                .toList();
    }

    public InventoryItem update(String sku, UpdateItemRequest req) {
        InventoryItem item = repository.getBySku(sku);
        if (req.expectedVersion() != null && !Objects.equals(req.expectedVersion(), item.getVersion())) {
            throw new ConflictException("Version mismatch for " + sku + ": expected " + req.expectedVersion()
                    + " but current is " + item.getVersion());
        }
        if (req.name() != null) {
            item.setName(req.name().trim());
        }
        if (req.category() != null) {
            item.setCategory(req.category().trim().toLowerCase());
        }
        if (req.unitPrice() != null) {
            item.setUnitPrice(req.unitPrice());
        }
        if (req.reorderLevel() != null) {
            item.setReorderLevel(req.reorderLevel());
        }
        return repository.update(item);
    }

    public void delete(String sku) {
        repository.delete(sku);
    }

    public InventoryItem restock(String sku, int quantity, String note) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Restock quantity must be positive");
        }
        return repository.adjustStock(sku, quantity);
    }

    public InventoryItem adjust(String sku, int delta, String note) {
        if (delta == 0) {
            throw new IllegalArgumentException("Adjustment must be non-zero");
        }
        return repository.adjustStock(sku, delta);
    }
}
