package com.wex.payments.transactions.common.constants;

public final class ValidationMessages {

    public static final String DESCRIPTION_REQUIRED = "Description is required.";
    public static final String DESCRIPTION_MAX_LENGTH = "Description must not exceed 50 characters.";
    public static final String TRANSACTION_DATE_REQUIRED = "Transaction date is required.";
    public static final String PURCHASE_AMOUNT_REQUIRED = "Purchase amount is required.";
    public static final String PURCHASE_AMOUNT_POSITIVE = "Purchase amount must be positive.";

    private ValidationMessages() {
    }
}
