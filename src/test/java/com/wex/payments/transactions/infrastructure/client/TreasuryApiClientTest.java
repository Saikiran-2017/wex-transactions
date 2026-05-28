package com.wex.payments.transactions.infrastructure.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import com.github.tomakehurst.wiremock.client.WireMock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.wex.payments.transactions.config.TreasuryApiProperties;
import com.wex.payments.transactions.config.WebClientConfig;
import com.wex.payments.transactions.domain.exception.CurrencyConversionException;
import com.wex.payments.transactions.domain.exception.TreasuryApiException;
import com.wex.payments.transactions.domain.model.TreasuryExchangeRate;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

class TreasuryApiClientTest {

    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2024, 10, 15);
    private static final String CURRENCY = "Euro Zone-Euro";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private TreasuryApiClient client;

    @BeforeEach
    void setUp() {
        WireMock.configureFor("localhost", wireMock.getPort());

        TreasuryApiProperties properties = new TreasuryApiProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setConnectTimeoutMs(3000);
        properties.setReadTimeoutMs(5000);

        client = new TreasuryApiClient(new WebClientConfig().treasuryWebClient(properties));
    }

    @Test
    void returnsExchangeRateAndBuildsExpectedQueryParameters() {
        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {
                                      "country_currency_desc": "Euro Zone-Euro",
                                      "exchange_rate": "0.924",
                                      "record_date": "2024-10-15"
                                    }
                                  ]
                                }
                                """)));

        TreasuryExchangeRate rate = client.getExchangeRate(CURRENCY, TRANSACTION_DATE);

        assertThat(rate.currency()).isEqualTo(CURRENCY);
        assertThat(rate.exchangeRate()).isEqualByComparingTo("0.924");
        assertThat(rate.recordDate()).isEqualTo(TRANSACTION_DATE);

        verify(getRequestedFor(urlPathEqualTo("/"))
                .withQueryParam("fields", equalTo("country_currency_desc,exchange_rate,record_date"))
                .withQueryParam("sort", equalTo("-record_date"))
                .withQueryParam("page[size]", equalTo("1"))
                .withQueryParam("filter", equalTo(
                        "country_currency_desc:eq:Euro Zone-Euro,record_date:lte:2024-10-15,record_date:gte:2024-04-15")));
    }

    @Test
    void throwsCurrencyConversionExceptionWhenDataArrayIsEmpty() {
        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"data\": [] }")));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(CurrencyConversionException.class)
                .hasMessage("Purchase cannot be converted to the target currency: " + CURRENCY);
    }

    @Test
    void throwsCurrencyConversionExceptionWhenDataIsNull() {
        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"meta\": {} }")));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(CurrencyConversionException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForInvalidExchangeRate() {
        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {
                                      "country_currency_desc": "Euro Zone-Euro",
                                      "exchange_rate": "abc",
                                      "record_date": "2024-10-15"
                                    }
                                  ]
                                }
                                """)));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForZeroExchangeRate() {
        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {
                                      "country_currency_desc": "Euro Zone-Euro",
                                      "exchange_rate": "0",
                                      "record_date": "2024-10-15"
                                    }
                                  ]
                                }
                                """)));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForNegativeExchangeRate() {
        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {
                                      "country_currency_desc": "Euro Zone-Euro",
                                      "exchange_rate": "-1.1",
                                      "record_date": "2024-10-15"
                                    }
                                  ]
                                }
                                """)));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForInvalidRecordDate() {
        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {
                                      "country_currency_desc": "Euro Zone-Euro",
                                      "exchange_rate": "0.924",
                                      "record_date": "not-a-date"
                                    }
                                  ]
                                }
                                """)));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForHttp500() {
        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"error\": \"service unavailable\" }")));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForHttp400() {
        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"error\": \"bad request\" }")));

        assertThatThrownBy(() -> client.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionForReadTimeout() {
        TreasuryApiProperties properties = new TreasuryApiProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(500);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .doOnConnected(connection -> connection.addHandlerLast(
                        new ReadTimeoutHandler(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

        TreasuryApiClient timeoutClient = new TreasuryApiClient(WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build());

        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {
                                      "country_currency_desc": "Euro Zone-Euro",
                                      "exchange_rate": "0.924",
                                      "record_date": "2024-10-15"
                                    }
                                  ]
                                }
                                """)));

        assertThatThrownBy(() -> timeoutClient.getExchangeRate(CURRENCY, TRANSACTION_DATE))
                .isInstanceOf(TreasuryApiException.class);
    }
}
