package com.wex.payments.transactions.domain.exception;

public class TreasuryApiException extends RuntimeException {

    public TreasuryApiException(String message) {
        super(message);
    }

    public TreasuryApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
