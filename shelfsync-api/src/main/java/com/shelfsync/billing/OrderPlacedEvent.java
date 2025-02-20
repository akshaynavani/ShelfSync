package com.shelfsync.billing;

/** Published inside the checkout transaction; listeners act on it after commit. */
public record OrderPlacedEvent(Long orderId, Long invoiceId, String orderNumber) {
}
