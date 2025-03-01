package com.shelfsync.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    void roundsHalfUpToCents() {
        assertThat(Money.round(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
        assertThat(Money.lineTotal(new BigDecimal("0.333"), 3)).isEqualByComparingTo("1.00");
        assertThat(Money.tax(new BigDecimal("19.25"), new BigDecimal("0.08"))).isEqualByComparingTo("1.54");
    }
}
