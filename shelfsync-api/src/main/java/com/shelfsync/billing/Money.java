package com.shelfsync.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Currency arithmetic helpers: everything is rounded half-up to cents. */
public final class Money {

    private Money() {
    }

    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return round(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    public static BigDecimal tax(BigDecimal subtotal, BigDecimal rate) {
        return round(subtotal.multiply(rate));
    }
}
