package com.wex.payments.transactions.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wex.payments.transactions.domain.exception.CurrencyConversionException;
import com.wex.payments.transactions.domain.exception.TreasuryApiException;
import com.wex.payments.transactions.domain.model.TreasuryExchangeRate;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

class TreasuryApiClientTest {

    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2024, 10, 15);
    private static final String CURRENCY = "Euro Zone-Euro";

    @Test
    void buildsExpectedQueryParametersAndReturnsRate() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        TreasuryApiClient client = clientWithExchange(request -> {
            capturedRequest.set(request);
            return jsonResponse(HttpStatus.OK, """
                    {
                      "data": [
                        {
                          "country_currency_desc": "Euro Zone-Euro",
                          "exchange_rate": "0.924",
                          "record_date": "2024-10-15"
                        }
                      ]
                    }
                    """);
        });

        TreasuryExchangeRate rate = client.getExchangeRate(CURRENCY, TRANSACTION_DATE);

        assertThat(rate.currency()).isEqualTo(CURRENCY);
        assertThat(rate.exchangeRate()).isEqualByComparingTo("0.924");
        assertThat(rate.recordDate()).isEqualTo(TRANSACTION_DATE);

        MultiValueMap<String, String> params = UriComponentsBuilder
                .fromUri(capturedRequest.get().url())
                .build()
                .getQueryParams();
        assertThat(params.getFirst("fields")).isEqualTo("country_currency_desc,exchange_rate,record_date");
        assertThat(params.getFirst("sort")).isEqualTo("-record_date");
        assertThat(capturedRequest.get().url().getQuery()).contains("page[size]=1");

        String filter = params.getFirst("filter");
        assertThat(filter).contains("country_currency_desc:eq:Euro%20Zone-Euro");
        assertThat(filter).contains("record_date:lte:2024-10-15");
        assertThat(filter).contains("record_date:gte:2024-04-15");
    }

    @Test
    void throwsCurrencyConversionExceptionWhenDataArrayIsEmpty() {
        TreasuryApiClient client = clientWithExchange(request -> jsonResponse(HttpStatus.OK, """
                { "data": [] }
                """));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(CurrencyConversionException.class);
    }

    @Test
    void throwsCurrencyConversionExceptionWhenDataIsNull() {
        TreasuryApiClient client = clientWithExchange(request -> jsonResponse(HttpStatus.OK, """
                { "meta": {} }
                """));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(CurrencyConversionException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForInvalidExchangeRate() {
        TreasuryApiClient client = clientWithExchange(request -> jsonResponse(HttpStatus.OK, """
                {
                  "data": [
                    {
                      "country_currency_desc": "Euro Zone-Euro",
                      "exchange_rate": "abc",
                      "record_date": "2024-10-15"
                    }
                  ]
                }
                """));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForZeroExchangeRate() {
        TreasuryApiClient client = clientWithExchange(request -> jsonResponse(HttpStatus.OK, """
                {
                  "data": [
                    {
                      "country_currency_desc": "Euro Zone-Euro",
                      "exchange_rate": "0",
                      "record_date": "2024-10-15"
                    }
                  ]
                }
                """));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForNegativeExchangeRate() {
        TreasuryApiClient client = clientWithExchange(request -> jsonResponse(HttpStatus.OK, """
                {
                  "data": [
                    {
                      "country_currency_desc": "Euro Zone-Euro",
                      "exchange_rate": "-1.1",
                      "record_date": "2024-10-15"
                    }
                  ]
                }
                """));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForInvalidRecordDate() {
        TreasuryApiClient client = clientWithExchange(request -> jsonResponse(HttpStatus.OK, """
                {
                  "data": [
                    {
                      "country_currency_desc": "Euro Zone-Euro",
                      "exchange_rate": "0.924",
                      "record_date": "not-a-date"
                    }
                  ]
                }
                """));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForHttp4xx() {
        TreasuryApiClient client = clientWithExchange(request -> jsonResponse(HttpStatus.BAD_REQUEST, """
                { "error": "bad request" }
                """));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForHttp5xx() {
        TreasuryApiClient client = clientWithExchange(request -> jsonResponse(HttpStatus.SERVICE_UNAVAILABLE, """
                { "error": "service unavailable" }
                """));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForConnectionFailure() {
        ExchangeFunction exchangeFunction = request -> Mono.error(new WebClientRequestException(
                new IOException("Connection refused"),
                HttpMethod.GET,
                URI.create("https://example.org"),
                HttpHeaders.EMPTY));
        TreasuryApiClient client = new TreasuryApiClient(WebClient.builder()
                .baseUrl("https://example.org")
                .exchangeFunction(exchangeFunction)
                .build());

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class)
                .hasMessageContaining("Treasury API request failed");
    }

    private TreasuryApiClient clientWithExchange(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://example.org")
                .exchangeFunction(exchangeFunction)
                .build();
        return new TreasuryApiClient(webClient);
    }

    private Mono<ClientResponse> jsonResponse(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}
