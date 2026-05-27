package com.wex.payments.transactions.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseTransaction(
        UUID id,
        String description,
        LocalDate transactionDate,
        BigDecimal purchaseAmount,
        OffsetDateTime createdAt
) {
}
