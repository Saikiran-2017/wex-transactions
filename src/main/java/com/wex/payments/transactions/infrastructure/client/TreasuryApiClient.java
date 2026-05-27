package com.wex.payments.transactions.infrastructure.client;

import com.wex.payments.transactions.domain.exception.CurrencyConversionException;
import com.wex.payments.transactions.domain.exception.TreasuryApiException;
import com.wex.payments.transactions.domain.model.TreasuryExchangeRate;
import com.wex.payments.transactions.infrastructure.client.dto.TreasuryExchangeRateResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Component
public class TreasuryApiClient {

    private static final Logger log = LoggerFactory.getLogger(TreasuryApiClient.class);

    private final WebClient treasuryWebClient;

    public TreasuryApiClient(@Qualifier("treasuryWebClient") WebClient treasuryWebClient) {
        this.treasuryWebClient = treasuryWebClient;
    }

    public TreasuryExchangeRate getExchangeRate(String currency, LocalDate transactionDate) {
        String normalizedCurrency = currency == null ? "" : currency.trim();
        if (normalizedCurrency.isBlank()) {
            throw new CurrencyConversionException(currency);
        }
        if (transactionDate == null) {
            throw new TreasuryApiException("Transaction date is required for treasury rate lookup");
        }

        LocalDate lowerBoundDate = transactionDate.minusMonths(6);
        String filter = "country_currency_desc:eq:" + normalizedCurrency
                + ",record_date:lte:" + transactionDate
                + ",record_date:gte:" + lowerBoundDate;

        TreasuryExchangeRateResponse response;
        try {
            response = treasuryWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("fields", "country_currency_desc,exchange_rate,record_date")
                            .queryParam("filter", filter)
                            .queryParam("sort", "-record_date")
                            .queryParam("page[size]", "1")
                            .build())
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new TreasuryApiException(
                                            "Treasury API returned error status " + clientResponse.statusCode().value())))
                    )
                    .bodyToMono(TreasuryExchangeRateResponse.class)
                    .onErrorMap(
                            WebClientRequestException.class,
                            ex -> new TreasuryApiException("Treasury API request failed", ex))
                    .block();
        } catch (TreasuryApiException ex) {
            log.warn("Treasury API lookup failed for currency={} transactionDate={}", normalizedCurrency, transactionDate);
            throw ex;
        } catch (Exception ex) {
            log.warn("Treasury API lookup failed for currency={} transactionDate={}", normalizedCurrency, transactionDate);
            throw new TreasuryApiException("Treasury API call failed unexpectedly", ex);
        }

        List<TreasuryExchangeRateResponse.TreasuryExchangeRateData> data = response != null ? response.getData() : null;
        if (data == null || data.isEmpty()) {
            throw new CurrencyConversionException(normalizedCurrency);
        }

        TreasuryExchangeRateResponse.TreasuryExchangeRateData firstRecord = data.get(0);
        BigDecimal exchangeRate = parseExchangeRate(firstRecord.getExchangeRate());
        LocalDate recordDate = firstRecord.getRecordDate();

        if (recordDate == null) {
            throw new TreasuryApiException("Treasury API returned an invalid record date");
        }

        return new TreasuryExchangeRate(normalizedCurrency, exchangeRate, recordDate);
    }

    private BigDecimal parseExchangeRate(String exchangeRateRaw) {
        if (exchangeRateRaw == null || exchangeRateRaw.isBlank()) {
            throw new TreasuryApiException("Treasury API returned an empty exchange rate");
        }

        try {
            BigDecimal exchangeRate = new BigDecimal(exchangeRateRaw.trim());
            if (exchangeRate.signum() <= 0) {
                throw new TreasuryApiException("Treasury API returned a non-positive exchange rate");
            }
            return exchangeRate;
        } catch (NumberFormatException ex) {
            throw new TreasuryApiException("Treasury API returned an invalid exchange rate format", ex);
        }
    }
}
