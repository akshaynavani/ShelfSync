package com.shelfsync.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findBySkuOrderByCreatedAtDesc(String sku, Pageable pageable);

    Page<StockMovement> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to, Pageable pageable);

    List<StockMovement> findByReferenceOrderByIdAsc(String reference);

    @Query("""
            select m.sku as sku, sum(-m.quantityDelta) as unitsSold
            from StockMovement m
            where m.type in (com.shelfsync.ledger.MovementType.SALE, com.shelfsync.ledger.MovementType.SALE_REVERSAL)
              and m.createdAt >= :from and m.createdAt < :to
            group by m.sku
            order by sum(-m.quantityDelta) desc
            """)
    List<SkuSalesView> salesBySku(@Param("from") Instant from, @Param("to") Instant to);

    interface SkuSalesView {
        String getSku();

        Long getUnitsSold();
    }
}
