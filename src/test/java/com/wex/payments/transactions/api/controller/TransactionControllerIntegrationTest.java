package com.wex.payments.transactions.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wex.payments.transactions.common.constants.ValidationMessages;
import com.wex.payments.transactions.domain.exception.CurrencyConversionException;
import com.wex.payments.transactions.domain.exception.TreasuryApiException;
import com.wex.payments.transactions.domain.model.TreasuryExchangeRate;
import com.wex.payments.transactions.infrastructure.client.TreasuryApiClient;
import com.wex.payments.transactions.infrastructure.persistence.entity.PurchaseTransactionEntity;
import com.wex.payments.transactions.infrastructure.persistence.repository.PurchaseTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionControllerIntegrationTest {

    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2024, 10, 15);
    private static final String CURRENCY = "Euro Zone-Euro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseTransactionRepository purchaseTransactionRepository;

    @MockBean
    private TreasuryApiClient treasuryApiClient;

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
        PurchaseTransactionEntity persisted = purchaseTransactionRepository.findById(id).orElseThrow();
        assertThat(persisted.getDescription()).isEqualTo("Office supplies purchase");
        assertThat(persisted.getTransactionDate()).isEqualTo(TRANSACTION_DATE);
        assertThat(persisted.getPurchaseAmount()).isEqualByComparingTo("149.99");
    }

    @Test
    void postTransactionsTrimsAndRoundsValues() throws Exception {
        MvcResult result = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "  Office supplies  ",
                                "transactionDate", "2024-10-15",
                                "purchaseAmount", 149.999
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Office supplies"))
                .andExpect(jsonPath("$.purchaseAmount").value(150.00))
                .andReturn();

        UUID id = UUID.fromString(readJson(result).get("id").asText());
        PurchaseTransactionEntity persisted = purchaseTransactionRepository.findById(id).orElseThrow();
        assertThat(persisted.getDescription()).isEqualTo("Office supplies");
        assertThat(persisted.getPurchaseAmount()).isEqualByComparingTo("150.00");
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
                .andExpect(jsonPath("$.message").value(ValidationMessages.DESCRIPTION_MAX_LENGTH));
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
    void postTransactionsRejectsMissingFields() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getTransactionInCurrencyReturnsConvertedTransaction() throws Exception {
        UUID transactionId = createTransactionViaPost("Office supplies purchase", "2024-10-15", 149.99);
        TreasuryExchangeRate rate = new TreasuryExchangeRate(
                CURRENCY,
                new BigDecimal("0.924"),
                TRANSACTION_DATE
        );
        when(treasuryApiClient.getExchangeRate(CURRENCY, TRANSACTION_DATE)).thenReturn(rate);

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
                .andExpect(jsonPath("$.message").value("Transaction not found with id: " + missingId));

        verify(treasuryApiClient, never()).getExchangeRate(any(), any());
    }

    @Test
    void getTransactionInCurrencyReturnsUnprocessableEntityWhenConversionFails() throws Exception {
        UUID transactionId = createTransactionViaPost("Office supplies purchase", "2024-10-15", 149.99);
        when(treasuryApiClient.getExchangeRate(eq(CURRENCY), eq(TRANSACTION_DATE)))
                .thenThrow(new CurrencyConversionException(CURRENCY));

        mockMvc.perform(get("/transactions/{id}", transactionId).param("currency", CURRENCY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message")
                        .value("Purchase cannot be converted to the target currency: " + CURRENCY));
    }

    @Test
    void getTransactionInCurrencyReturnsServiceUnavailableWhenTreasuryApiFails() throws Exception {
        UUID transactionId = createTransactionViaPost("Office supplies purchase", "2024-10-15", 149.99);
        when(treasuryApiClient.getExchangeRate(eq(CURRENCY), eq(TRANSACTION_DATE)))
                .thenThrow(new TreasuryApiException("Treasury API unavailable"));

        mockMvc.perform(get("/transactions/{id}", transactionId).param("currency", CURRENCY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message")
                        .value("Treasury exchange rate service is currently unavailable"));
    }

    @Test
    void getTransactionInCurrencyReturnsBadRequestForInvalidUuid() throws Exception {
        mockMvc.perform(get("/transactions/{id}", "not-a-uuid").param("currency", CURRENCY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for request parameter: id"));
    }

    @Test
    void getTransactionInCurrencyRequiresCurrencyQueryParam() throws Exception {
        UUID transactionId = createTransactionViaPost("Office supplies purchase", "2024-10-15", 149.99);

        mockMvc.perform(get("/transactions/{id}", transactionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Required request parameter is missing: currency"));
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
