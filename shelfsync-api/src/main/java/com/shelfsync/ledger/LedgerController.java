package com.shelfsync.ledger;

import com.shelfsync.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledger;

    public LedgerController(LedgerService ledger) {
        this.ledger = ledger;
    }

    public record MovementResponse(Long id, String sku, MovementType type, int quantityDelta, int stockAfter,
                                   String reference, String note, Instant createdAt) {
        static MovementResponse from(StockMovement m) {
            return new MovementResponse(m.getId(), m.getSku(), m.getType(), m.getQuantityDelta(),
                    m.getStockAfter(), m.getReference(), m.getNote(), m.getCreatedAt());
        }
    }

    public record SkuSales(String sku, long unitsSold) {
    }

    @GetMapping("/items/{sku}")
    public PageResponse<MovementResponse> forSku(@PathVariable String sku,
                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return PageResponse.of(ledger.historyForSku(sku, page, size), MovementResponse::from);
    }

    @GetMapping("/movements")
    public PageResponse<MovementResponse> between(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        requireRange(from, to);
        return PageResponse.of(ledger.historyBetween(from, to, page, size), MovementResponse::from);
    }

    /** Net units sold per SKU in [from, to), cancellations subtracted. */
    @GetMapping("/sales")
    public List<SkuSales> sales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        requireRange(from, to);
        return ledger.salesBySku(from, to).stream()
                .map(v -> new SkuSales(v.getSku(), v.getUnitsSold() == null ? 0 : v.getUnitsSold()))
                .toList();
    }

    private static void requireRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
    }
}
