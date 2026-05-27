package com.wex.payments.transactions.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TreasuryExchangeRate(
        String currency,
        BigDecimal exchangeRate,
        LocalDate recordDate
) {
}
