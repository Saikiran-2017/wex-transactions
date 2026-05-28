package com.wex.payments.transactions.application.service;

import com.wex.payments.transactions.api.dto.request.CreateTransactionRequest;
import com.wex.payments.transactions.api.dto.response.ConvertedTransactionResponse;
import com.wex.payments.transactions.api.dto.response.TransactionResponse;
import com.wex.payments.transactions.domain.exception.CurrencyConversionException;
import com.wex.payments.transactions.domain.exception.InvalidTransactionException;
import com.wex.payments.transactions.domain.exception.TransactionNotFoundException;
import com.wex.payments.transactions.domain.model.TreasuryExchangeRate;
import com.wex.payments.transactions.infrastructure.client.TreasuryApiClient;
import com.wex.payments.transactions.infrastructure.persistence.entity.PurchaseTransactionEntity;
import com.wex.payments.transactions.infrastructure.persistence.repository.PurchaseTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final PurchaseTransactionRepository purchaseTransactionRepository;
    private final TreasuryApiClient treasuryApiClient;

    public TransactionService(
            PurchaseTransactionRepository purchaseTransactionRepository,
            TreasuryApiClient treasuryApiClient
    ) {
        this.purchaseTransactionRepository = purchaseTransactionRepository;
        this.treasuryApiClient = treasuryApiClient;
    }

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        BigDecimal roundedPurchaseAmount = roundMoney(request.getPurchaseAmount());
        if (roundedPurchaseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("purchaseAmount must round to a positive cent value");
        }

        PurchaseTransactionEntity entity = PurchaseTransactionEntity.builder()
                .description(request.getDescription().trim())
                .transactionDate(request.getTransactionDate())
                .purchaseAmount(roundedPurchaseAmount)
                .build();

        PurchaseTransactionEntity saved = purchaseTransactionRepository.save(entity);
        log.info("Transaction created successfully id={} transactionDate={}", saved.getId(), saved.getTransactionDate());
        return toTransactionResponse(saved);
    }

    @Transactional(readOnly = true)
    public ConvertedTransactionResponse getTransactionInCurrency(UUID id, String currency) {
        PurchaseTransactionEntity entity = purchaseTransactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        String normalizedCurrency = currency == null ? "" : currency.trim();
        if (normalizedCurrency.isBlank()) {
            throw new CurrencyConversionException(currency);
        }

        log.info(
                "Transaction conversion requested id={} currency={} transactionDate={}",
                entity.getId(),
                normalizedCurrency,
                entity.getTransactionDate()
        );

        TreasuryExchangeRate exchangeRate = treasuryApiClient.getExchangeRate(
                normalizedCurrency,
                entity.getTransactionDate()
        );

        return toConvertedTransactionResponse(entity, exchangeRate, normalizedCurrency);
    }

    private TransactionResponse toTransactionResponse(PurchaseTransactionEntity entity) {
        return TransactionResponse.builder()
                .id(entity.getId())
                .description(entity.getDescription())
                .transactionDate(entity.getTransactionDate())
                .purchaseAmount(entity.getPurchaseAmount())
                .build();
    }

    private ConvertedTransactionResponse toConvertedTransactionResponse(
            PurchaseTransactionEntity entity,
            TreasuryExchangeRate rate,
            String currency
    ) {
        BigDecimal convertedAmount = roundMoney(entity.getPurchaseAmount().multiply(rate.exchangeRate()));
        return ConvertedTransactionResponse.builder()
                .id(entity.getId())
                .description(entity.getDescription())
                .transactionDate(entity.getTransactionDate())
                .originalAmountUsd(entity.getPurchaseAmount())
                .exchangeRate(rate.exchangeRate())
                .convertedAmount(convertedAmount)
                .currency(currency)
                .build();
    }

    private BigDecimal roundMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
