package com.shelfsync.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByOrderId(Long orderId);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /** Invoices that still need a PDF: pending ones older than the grace period, or failed ones under the attempt cap. */
    @Query("""
            select i.id from Invoice i
            where i.status in :statuses and i.attempts < :maxAttempts and i.createdAt < :createdBefore
            order by i.createdAt asc
            """)
    List<Long> findRetryCandidates(@Param("statuses") Collection<InvoiceStatus> statuses,
                                   @Param("maxAttempts") int maxAttempts,
                                   @Param("createdBefore") Instant createdBefore);
}
