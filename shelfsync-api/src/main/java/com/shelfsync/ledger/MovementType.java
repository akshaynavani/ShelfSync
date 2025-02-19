package com.shelfsync.ledger;

public enum MovementType {
    /** Opening balance when an item is created. */
    INITIAL,
    RESTOCK,
    /** Manual correction: breakage, shrinkage, stock-take. */
    ADJUSTMENT,
    SALE,
    /** Stock returned because an order was cancelled or a checkout was rolled back. */
    SALE_REVERSAL
}
