package com.shelfsync.billing;

import com.shelfsync.billing.dto.CheckoutRequest;
import com.shelfsync.billing.dto.OrderResponse;
import com.shelfsync.billing.dto.OrderSummary;
import com.shelfsync.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final BillingService billing;

    public OrderController(BillingService billing) {
        this.billing = billing;
    }

    /**
     * Places an order. Send an {@code Idempotency-Key} header to make retries safe: a repeated key
     * returns the original order with 200 instead of creating a second one.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 64) String idempotencyKey) {
        BillingService.CheckoutResult result = billing.checkout(request, idempotencyKey);
        OrderResponse body = OrderResponse.from(result.order(), result.invoice());
        if (result.replayed()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(ServletUriComponentsBuilder.fromCurrentRequest().path("/{number}")
                        .buildAndExpand(result.order().getOrderNumber()).toUri())
                .body(body);
    }

    @GetMapping
    public PageResponse<OrderSummary> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return PageResponse.of(billing.list(page, size), OrderSummary::from);
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse get(@PathVariable String orderNumber) {
        BillingService.CheckoutResult result = billing.get(orderNumber);
        return OrderResponse.from(result.order(), result.invoice());
    }

    @PostMapping("/{orderNumber}/cancel")
    public OrderResponse cancel(@PathVariable String orderNumber) {
        BillingService.CheckoutResult result = billing.cancel(orderNumber);
        return OrderResponse.from(result.order(), result.invoice());
    }
}
