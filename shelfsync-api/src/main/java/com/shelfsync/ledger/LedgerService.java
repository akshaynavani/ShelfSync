package com.shelfsync.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class LedgerService {

    private final StockMovementRepository repository;
    private final Clock clock;

    public LedgerService(StockMovementRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Joins the caller's transaction when there is one (checkout), otherwise opens its own. */
    @Transactional(propagation = Propagation.REQUIRED)
    public StockMovement record(String sku, MovementType type, int delta, int stockAfter,
                                String reference, String note) {
        return repository.save(new StockMovement(sku, type, delta, stockAfter, reference, note, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> historyForSku(String sku, int page, int size) {
        return repository.findBySkuOrderByCreatedAtDesc(sku, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> historyBetween(Instant from, Instant to, int page, int size) {
        return repository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<StockMovementRepository.SkuSalesView> salesBySku(Instant from, Instant to) {
        return repository.salesBySku(from, to);
    }
}
