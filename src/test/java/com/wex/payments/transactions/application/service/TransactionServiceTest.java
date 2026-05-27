package com.wex.payments.transactions.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wex.payments.transactions.api.dto.request.CreateTransactionRequest;
import com.wex.payments.transactions.api.dto.response.ConvertedTransactionResponse;
import com.wex.payments.transactions.api.dto.response.TransactionResponse;
import com.wex.payments.transactions.domain.exception.CurrencyConversionException;
import com.wex.payments.transactions.domain.exception.TransactionNotFoundException;
import com.wex.payments.transactions.domain.exception.TreasuryApiException;
import com.wex.payments.transactions.domain.model.TreasuryExchangeRate;
import com.wex.payments.transactions.infrastructure.client.TreasuryApiClient;
import com.wex.payments.transactions.infrastructure.persistence.entity.PurchaseTransactionEntity;
import com.wex.payments.transactions.infrastructure.persistence.repository.PurchaseTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2024, 10, 15);
    private static final UUID TRANSACTION_ID = UUID.fromString("48d8e4ad-e1f7-4c8d-a3b5-b6ca8c0b68ef");
    private static final String CURRENCY = "Euro Zone-Euro";

    @Mock
    private PurchaseTransactionRepository purchaseTransactionRepository;

    @Mock
    private TreasuryApiClient treasuryApiClient;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void createsTransactionAndReturnsResponseWithGeneratedId() {
        CreateTransactionRequest request = createRequest("Office supplies", "149.99");
        PurchaseTransactionEntity savedEntity = buildEntity(TRANSACTION_ID, "Office supplies", "149.99");

        when(purchaseTransactionRepository.save(any(PurchaseTransactionEntity.class))).thenReturn(savedEntity);

        TransactionResponse response = transactionService.createTransaction(request);

        assertThat(response.getId()).isEqualTo(TRANSACTION_ID);
        assertThat(response.getDescription()).isEqualTo("Office supplies");
        assertThat(response.getTransactionDate()).isEqualTo(TRANSACTION_DATE);
        assertThat(response.getPurchaseAmount()).isEqualByComparingTo("149.99");
    }

    @Test
    void trimsDescriptionBeforeStoring() {
        CreateTransactionRequest request = createRequest("  Office supplies  ", "149.99");
        PurchaseTransactionEntity savedEntity = buildEntity(TRANSACTION_ID, "Office supplies", "149.99");
        ArgumentCaptor<PurchaseTransactionEntity> entityCaptor = ArgumentCaptor.forClass(PurchaseTransactionEntity.class);

        when(purchaseTransactionRepository.save(any(PurchaseTransactionEntity.class))).thenReturn(savedEntity);

        transactionService.createTransaction(request);

        verify(purchaseTransactionRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getDescription()).isEqualTo("Office supplies");
    }

    @Test
    void roundsPurchaseAmountHalfUpWhenStoring() {
        CreateTransactionRequest request = createRequest("Office supplies", "149.999");
        PurchaseTransactionEntity savedEntity = buildEntity(TRANSACTION_ID, "Office supplies", "150.00");
        ArgumentCaptor<PurchaseTransactionEntity> entityCaptor = ArgumentCaptor.forClass(PurchaseTransactionEntity.class);

        when(purchaseTransactionRepository.save(any(PurchaseTransactionEntity.class))).thenReturn(savedEntity);

        transactionService.createTransaction(request);

        verify(purchaseTransactionRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getPurchaseAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void roundsHalfCentCorrectly() {
        CreateTransactionRequest request = createRequest("Office supplies", "100.005");
        PurchaseTransactionEntity savedEntity = buildEntity(TRANSACTION_ID, "Office supplies", "100.01");
        ArgumentCaptor<PurchaseTransactionEntity> entityCaptor = ArgumentCaptor.forClass(PurchaseTransactionEntity.class);

        when(purchaseTransactionRepository.save(any(PurchaseTransactionEntity.class))).thenReturn(savedEntity);

        transactionService.createTransaction(request);

        verify(purchaseTransactionRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getPurchaseAmount()).isEqualByComparingTo("100.01");
    }

    @Test
    void doesNotCallTreasuryApiClientDuringCreateTransaction() {
        CreateTransactionRequest request = createRequest("Office supplies", "149.99");
        PurchaseTransactionEntity savedEntity = buildEntity(TRANSACTION_ID, "Office supplies", "149.99");

        when(purchaseTransactionRepository.save(any(PurchaseTransactionEntity.class))).thenReturn(savedEntity);

        transactionService.createTransaction(request);

        verifyNoInteractions(treasuryApiClient);
    }

    @Test
    void returnsConvertedTransactionUsingTreasuryExchangeRate() {
        PurchaseTransactionEntity existingEntity = buildEntity(TRANSACTION_ID, "Office supplies", "149.99");
        TreasuryExchangeRate rate = new TreasuryExchangeRate(CURRENCY, new BigDecimal("0.924"), TRANSACTION_DATE);

        when(purchaseTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(existingEntity));
        when(treasuryApiClient.getExchangeRate(CURRENCY, TRANSACTION_DATE)).thenReturn(rate);

        ConvertedTransactionResponse response = transactionService.getTransactionInCurrency(TRANSACTION_ID, CURRENCY);

        assertThat(response.getId()).isEqualTo(TRANSACTION_ID);
        assertThat(response.getDescription()).isEqualTo("Office supplies");
        assertThat(response.getTransactionDate()).isEqualTo(TRANSACTION_DATE);
        assertThat(response.getOriginalAmountUsd()).isEqualByComparingTo("149.99");
        assertThat(response.getExchangeRate()).isEqualByComparingTo("0.924");
        assertThat(response.getConvertedAmount()).isEqualByComparingTo("138.59");
        assertThat(response.getCurrency()).isEqualTo(CURRENCY);
    }

    @Test
    void trimsCurrencyBeforeCallingTreasuryApiClient() {
        PurchaseTransactionEntity existingEntity = buildEntity(TRANSACTION_ID, "Office supplies", "149.99");
        TreasuryExchangeRate rate = new TreasuryExchangeRate(CURRENCY, new BigDecimal("0.924"), TRANSACTION_DATE);

        when(purchaseTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(existingEntity));
        when(treasuryApiClient.getExchangeRate(CURRENCY, TRANSACTION_DATE)).thenReturn(rate);

        transactionService.getTransactionInCurrency(TRANSACTION_ID, "  Euro Zone-Euro  ");

        verify(treasuryApiClient).getExchangeRate("Euro Zone-Euro", TRANSACTION_DATE);
    }

    @Test
    void throwsTransactionNotFoundExceptionWhenTransactionMissing() {
        when(purchaseTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransactionInCurrency(TRANSACTION_ID, CURRENCY))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(TRANSACTION_ID.toString());

        verify(treasuryApiClient, never()).getExchangeRate(any(), any());
    }

    @Test
    void throwsCurrencyConversionExceptionWhenCurrencyIsNull() {
        PurchaseTransactionEntity existingEntity = buildEntity(TRANSACTION_ID, "Office supplies", "149.99");
        when(purchaseTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(existingEntity));

        assertThatThrownBy(() -> transactionService.getTransactionInCurrency(TRANSACTION_ID, null))
                .isInstanceOf(CurrencyConversionException.class);

        verify(treasuryApiClient, never()).getExchangeRate(any(), any());
    }

    @Test
    void throwsCurrencyConversionExceptionWhenCurrencyIsBlank() {
        PurchaseTransactionEntity existingEntity = buildEntity(TRANSACTION_ID, "Office supplies", "149.99");
        when(purchaseTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(existingEntity));

        assertThatThrownBy(() -> transactionService.getTransactionInCurrency(TRANSACTION_ID, "   "))
                .isInstanceOf(CurrencyConversionException.class);

        verify(treasuryApiClient, never()).getExchangeRate(any(), any());
    }

    @Test
    void propagatesCurrencyConversionExceptionFromTreasuryApiClient() {
        PurchaseTransactionEntity existingEntity = buildEntity(TRANSACTION_ID, "Office supplies", "149.99");
        when(purchaseTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(existingEntity));
        when(treasuryApiClient.getExchangeRate(eq(CURRENCY), eq(TRANSACTION_DATE)))
                .thenThrow(new CurrencyConversionException(CURRENCY));

        assertThatThrownBy(() -> transactionService.getTransactionInCurrency(TRANSACTION_ID, CURRENCY))
                .isInstanceOf(CurrencyConversionException.class);
    }

    @Test
    void propagatesTreasuryApiExceptionFromTreasuryApiClient() {
        PurchaseTransactionEntity existingEntity = buildEntity(TRANSACTION_ID, "Office supplies", "149.99");
        when(purchaseTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(existingEntity));
        when(treasuryApiClient.getExchangeRate(eq(CURRENCY), eq(TRANSACTION_DATE)))
                .thenThrow(new TreasuryApiException("Treasury API unavailable"));

        assertThatThrownBy(() -> transactionService.getTransactionInCurrency(TRANSACTION_ID, CURRENCY))
                .isInstanceOf(TreasuryApiException.class);
    }

    private CreateTransactionRequest createRequest(String description, String purchaseAmount) {
        return CreateTransactionRequest.builder()
                .description(description)
                .transactionDate(TRANSACTION_DATE)
                .purchaseAmount(new BigDecimal(purchaseAmount))
                .build();
    }

    private PurchaseTransactionEntity buildEntity(UUID id, String description, String purchaseAmount) {
        return PurchaseTransactionEntity.builder()
                .id(id)
                .description(description)
                .transactionDate(TRANSACTION_DATE)
                .purchaseAmount(new BigDecimal(purchaseAmount))
                .build();
    }
}
