package com.shelfsync.billing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    @EntityGraph(attributePaths = "lines")
    Optional<SalesOrder> findByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = "lines")
    Optional<SalesOrder> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = "lines")
    Optional<SalesOrder> findWithLinesById(Long id);

    Page<SalesOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
