package com.wex.payments.transactions.domain.exception;

public class CurrencyConversionException extends RuntimeException {

    public CurrencyConversionException(String currency) {
        super("Purchase cannot be converted to the target currency: " + currency);
    }
}
