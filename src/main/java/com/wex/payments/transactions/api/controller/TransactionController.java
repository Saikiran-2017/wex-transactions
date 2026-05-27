package com.wex.payments.transactions.api.controller;

import com.wex.payments.transactions.api.dto.request.CreateTransactionRequest;
import com.wex.payments.transactions.api.dto.response.ConvertedTransactionResponse;
import com.wex.payments.transactions.api.dto.response.TransactionResponse;
import com.wex.payments.transactions.application.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Endpoints for storing and converting purchase transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @Operation(
            summary = "Store a purchase transaction",
            description = "Creates and persists a purchase transaction in USD and returns the stored transaction payload"
    )
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        TransactionResponse response = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Retrieve a transaction in target currency",
            description = "Fetches a transaction by id and returns conversion details for the requested currency"
    )
    public ResponseEntity<ConvertedTransactionResponse> getTransactionInCurrency(
            @PathVariable UUID id,
            @RequestParam String currency
    ) {
        ConvertedTransactionResponse response = transactionService.getTransactionInCurrency(id, currency);
        return ResponseEntity.ok(response);
    }
}
