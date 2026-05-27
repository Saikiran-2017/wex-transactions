package com.wex.payments.transactions.api.dto.request;

import com.wex.payments.transactions.common.constants.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransactionRequest {

    @NotBlank(message = ValidationMessages.DESCRIPTION_REQUIRED)
    @Size(max = 50, message = ValidationMessages.DESCRIPTION_MAX_LENGTH)
    private String description;

    @NotNull(message = ValidationMessages.TRANSACTION_DATE_REQUIRED)
    private LocalDate transactionDate;

    @NotNull(message = ValidationMessages.PURCHASE_AMOUNT_REQUIRED)
    @Positive(message = ValidationMessages.PURCHASE_AMOUNT_POSITIVE)
    private BigDecimal purchaseAmount;
}
