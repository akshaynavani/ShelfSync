package com.shelfsync.inventory;

import com.shelfsync.common.ConflictException;
import com.shelfsync.inventory.dto.CreateItemRequest;
import com.shelfsync.inventory.dto.UpdateItemRequest;
import com.shelfsync.ledger.LedgerService;
import com.shelfsync.ledger.MovementType;
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
    private final LedgerService ledger;

    public InventoryService(InventoryRepository repository, LedgerService ledger) {
        this.repository = repository;
        this.ledger = ledger;
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
        if (created.getStock() > 0) {
            ledger.record(created.getSku(), MovementType.INITIAL, created.getStock(), created.getStock(),
                    null, "Opening balance");
        }
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
        return applyAndRecord(sku, quantity, MovementType.RESTOCK, note);
    }

    public InventoryItem adjust(String sku, int delta, String note) {
        if (delta == 0) {
            throw new IllegalArgumentException("Adjustment must be non-zero");
        }
        return applyAndRecord(sku, delta, MovementType.ADJUSTMENT, note);
    }

    /**
     * Applies the stock change in DynamoDB, then writes the ledger row to MySQL. If the ledger
     * write fails the DynamoDB change is reverted so the two stores do not drift apart.
     */
    private InventoryItem applyAndRecord(String sku, int delta, MovementType type, String note) {
        InventoryItem updated = repository.adjustStock(sku, delta);
        try {
            ledger.record(sku, type, delta, updated.getStock(), null, note);
        } catch (RuntimeException e) {
            log.error("Ledger write failed for {} {} {}; reverting stock change", type, sku, delta, e);
            try {
                repository.adjustStock(sku, -delta);
            } catch (RuntimeException revertFailure) {
                log.error("Could not revert stock change on {} by {}; manual reconciliation needed",
                        sku, delta, revertFailure);
                e.addSuppressed(revertFailure);
            }
            throw e;
        }
        return updated;
    }
}
