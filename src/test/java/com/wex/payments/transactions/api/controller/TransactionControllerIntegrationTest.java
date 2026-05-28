package com.wex.payments.transactions.api.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wex.payments.transactions.common.constants.ValidationMessages;
import com.wex.payments.transactions.infrastructure.persistence.entity.PurchaseTransactionEntity;
import com.wex.payments.transactions.infrastructure.persistence.repository.PurchaseTransactionRepository;
import com.wex.payments.transactions.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionControllerIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2024, 10, 15);
    private static final String CURRENCY = "Euro Zone-Euro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseTransactionRepository purchaseTransactionRepository;

    @BeforeEach
    void setUp() {
        purchaseTransactionRepository.deleteAll();
    }

    @Test
    void postTransactionsCreatesTransactionSuccessfully() throws Exception {
        MvcResult result = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "Office supplies purchase",
                                "transactionDate", "2024-10-15",
                                "purchaseAmount", 149.99
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.description").value("Office supplies purchase"))
                .andExpect(jsonPath("$.transactionDate").value("2024-10-15"))
                .andExpect(jsonPath("$.purchaseAmount").value(149.99))
                .andReturn();

        UUID id = UUID.fromString(readJson(result).get("id").asText());
        assertThat(id).isNotNull();
        PurchaseTransactionEntity persisted = purchaseTransactionRepository.findById(id).orElseThrow();
        assertThat(persisted.getDescription()).isEqualTo("Office supplies purchase");
        assertThat(persisted.getTransactionDate()).isEqualTo(TRANSACTION_DATE);
        assertThat(persisted.getPurchaseAmount()).isEqualByComparingTo("149.99");
    }

    @Test
    void postTransactionsTrimsDescription() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "  Office supplies  ",
                                "transactionDate", "2024-10-15",
                                "purchaseAmount", 149.99
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Office supplies"));
    }

    @Test
    void postTransactionsRoundsSubCentAmountHalfUp() throws Exception {
        MvcResult result = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "Rounding test",
                                "transactionDate", "2024-10-15",
                                "purchaseAmount", 100.005
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseAmount").value(100.01))
                .andReturn();

        UUID id = UUID.fromString(readJson(result).get("id").asText());
        PurchaseTransactionEntity persisted = purchaseTransactionRepository.findById(id).orElseThrow();
        assertThat(persisted.getPurchaseAmount()).isEqualByComparingTo("100.01");
    }

    @Test
    void postTransactionsRoundsPurchaseAmountHalfUp() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "Office supplies",
                                "transactionDate", "2024-10-15",
                                "purchaseAmount", 149.999
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseAmount").value(150.00));
    }

    @Test
    void postTransactionsRejectsDescriptionLongerThanFiftyCharacters() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "a".repeat(51),
                                "transactionDate", "2024-10-15",
                                "purchaseAmount", 149.99
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(ValidationMessages.DESCRIPTION_MAX_LENGTH))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void postTransactionsRejectsBlankDescription() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "   ",
                                "transactionDate", "2024-10-15",
                                "purchaseAmount", 149.99
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(ValidationMessages.DESCRIPTION_REQUIRED))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void postTransactionsRejectsNullDescription() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": null,
                                  "transactionDate": "2024-10-15",
                                  "purchaseAmount": 149.99
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(ValidationMessages.DESCRIPTION_REQUIRED));
    }

    @Test
    void postTransactionsRejectsNegativeAmount() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "Office supplies",
                                "transactionDate", "2024-10-15",
                                "purchaseAmount", -10.00
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(ValidationMessages.PURCHASE_AMOUNT_POSITIVE));
    }

    @Test
    void postTransactionsRejectsZeroAmount() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "Office supplies",
                                "transactionDate", "2024-10-15",
                                "purchaseAmount", 0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(ValidationMessages.PURCHASE_AMOUNT_POSITIVE));
    }

    @Test
    void postTransactionsRejectsNullAmount() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Office supplies",
                                  "transactionDate": "2024-10-15",
                                  "purchaseAmount": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(ValidationMessages.PURCHASE_AMOUNT_REQUIRED));
    }

    @Test
    void postTransactionsRejectsAmountThatRoundsToZero() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "Office supplies",
                                "transactionDate", "2024-10-15",
                                "purchaseAmount", 0.001
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("purchaseAmount must round to a positive cent value"));
    }

    @Test
    void postTransactionsRejectsMissingDescription() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionDate": "2024-10-15",
                                  "purchaseAmount": 149.99
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(ValidationMessages.DESCRIPTION_REQUIRED));
    }

    @Test
    void postTransactionsRejectsNullTransactionDate() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Office supplies",
                                  "transactionDate": null,
                                  "purchaseAmount": 149.99
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(ValidationMessages.TRANSACTION_DATE_REQUIRED));
    }

    @Test
    void postTransactionsRejectsMissingTransactionDate() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Office supplies",
                                  "purchaseAmount": 149.99
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(ValidationMessages.TRANSACTION_DATE_REQUIRED));
    }

    @Test
    void postTransactionsRejectsInvalidDateFormat() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Office supplies",
                                  "transactionDate": "15-10-2024",
                                  "purchaseAmount": 149.99
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void postTransactionsRejectsInvalidDate() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Office supplies",
                                  "transactionDate": "2024-13-45",
                                  "purchaseAmount": 149.99
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void postTransactionsRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Office supplies\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getTransactionInCurrencyReturnsConvertedTransaction() throws Exception {
        stubTreasurySuccess();
        UUID transactionId = createTransactionViaPost("Office supplies purchase", "2024-10-15", 149.99);

        mockMvc.perform(get("/transactions/{id}", transactionId).param("currency", CURRENCY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId.toString()))
                .andExpect(jsonPath("$.description").value("Office supplies purchase"))
                .andExpect(jsonPath("$.transactionDate").value("2024-10-15"))
                .andExpect(jsonPath("$.originalAmountUsd").value(149.99))
                .andExpect(jsonPath("$.exchangeRate").value(0.924))
                .andExpect(jsonPath("$.convertedAmount").value(138.59))
                .andExpect(jsonPath("$.currency").value(CURRENCY));
    }

    @Test
    void getTransactionInCurrencyReturnsNotFoundForMissingTransaction() throws Exception {
        UUID missingId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        mockMvc.perform(get("/transactions/{id}", missingId).param("currency", CURRENCY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Transaction not found with id: " + missingId))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getTransactionInCurrencyReturnsUnprocessableEntityWhenConversionFails() throws Exception {
        stubTreasuryEmpty();
        UUID transactionId = createTransactionViaPost("Office supplies purchase", "2024-10-15", 149.99);

        mockMvc.perform(get("/transactions/{id}", transactionId).param("currency", CURRENCY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message")
                        .value("Purchase cannot be converted to the target currency: " + CURRENCY))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getTransactionInCurrencyReturnsServiceUnavailableWhenTreasuryApiFails() throws Exception {
        stubTreasuryServerError();
        UUID transactionId = createTransactionViaPost("Office supplies purchase", "2024-10-15", 149.99);

        mockMvc.perform(get("/transactions/{id}", transactionId).param("currency", CURRENCY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message")
                        .value("Treasury exchange rate service is currently unavailable"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getTransactionInCurrencyReturnsBadRequestForInvalidUuid() throws Exception {
        mockMvc.perform(get("/transactions/{id}", "not-a-uuid").param("currency", CURRENCY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for request parameter: id"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getTransactionInCurrencyRequiresCurrencyQueryParam() throws Exception {
        UUID transactionId = createTransactionViaPost("Office supplies purchase", "2024-10-15", 149.99);

        mockMvc.perform(get("/transactions/{id}", transactionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Required request parameter is missing: currency"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getTransactionInCurrencyReturnsUnprocessableEntityForBlankCurrency() throws Exception {
        UUID transactionId = createTransactionViaPost("Office supplies purchase", "2024-10-15", 149.99);

        mockMvc.perform(get("/transactions/{id}", transactionId).param("currency", "   "))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Purchase cannot be converted to the target currency:    "));
    }

    private void stubTreasurySuccess() {
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
    }

    private void stubTreasuryEmpty() {
        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"data\": [] }")));
    }

    private void stubTreasuryServerError() {
        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"error\": \"service unavailable\" }")));
    }

    private UUID createTransactionViaPost(String description, String transactionDate, double purchaseAmount)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", description,
                                "transactionDate", transactionDate,
                                "purchaseAmount", purchaseAmount
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).get("id").asText());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
