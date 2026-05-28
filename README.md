# WEX Corporate Payments - Purchase Transaction Management System

## Overview
This service stores USD purchase transactions and retrieves converted amounts using U.S. Treasury Reporting Rates of Exchange.
Built as a Spring Boot microservice with layered architecture, Flyway migrations, externalized configuration, and automated tests.

## Tech Stack
- Java 17
- Spring Boot 3
- PostgreSQL
- Flyway
- WebClient
- Docker
- JUnit 5
- Mockito
- MockMvc

## Features
- Store purchase transactions
- Retrieve converted transactions
- Treasury API integration
- Validation and exception handling
- Automated testing
- Docker support

## Prerequisites
- Java 17+
- Docker Desktop (for containerized run)
- Maven Wrapper included (`mvnw` / `mvnw.cmd`)

## API Endpoints

### POST /transactions
Creates and stores a purchase transaction.

Example request:
```json
{
  "description": "Office supplies purchase",
  "transactionDate": "2024-10-15",
  "purchaseAmount": 149.99
}
```

Example response (`201 Created`):
```json
{
  "id": "b532f5f0-3f57-4604-9eea-73f1f9d70484",
  "description": "Office supplies purchase",
  "transactionDate": "2024-10-15",
  "purchaseAmount": 149.99
}
```

### GET /transactions/{id}?currency=Euro Zone-Euro
Returns a stored transaction converted to the requested currency.

Example request:
```text
GET /transactions/b532f5f0-3f57-4604-9eea-73f1f9d70484?currency=Euro Zone-Euro
```

Example response (`200 OK`):
```json
{
  "id": "b532f5f0-3f57-4604-9eea-73f1f9d70484",
  "description": "Office supplies purchase",
  "transactionDate": "2024-10-15",
  "originalAmountUsd": 149.99,
  "exchangeRate": 0.924,
  "convertedAmount": 138.59,
  "currency": "Euro Zone-Euro"
}
```

### Error response format
All API errors return:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Description is required.",
  "timestamp": "2026-05-27T21:00:00Z"
}
```

Common status codes:
- `400` validation/input errors
- `404` transaction not found
- `422` currency conversion unavailable
- `503` Treasury API unavailable

## Running Locally

### Option A: Maven + PostgreSQL container
```powershell
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

### Option B: Full Docker stack (app + postgres)
```powershell
docker compose up --build
```

Application URL: `http://localhost:8080`

## Running Tests
```powershell
.\mvnw.cmd clean test
```

## Swagger
- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

## Health
- Health endpoint: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

## Manual API Verification (PowerShell)

Start the application first (`docker compose up --build` or Maven + Postgres), then run:

```powershell
# Health check
Invoke-RestMethod http://localhost:8080/actuator/health

# Create transaction
$body = @{
  description = "Office supplies purchase"
  transactionDate = "2024-10-15"
  purchaseAmount = 149.99
} | ConvertTo-Json

$created = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/transactions" `
  -ContentType "application/json" `
  -Body $body

$created

# Convert transaction
$id = $created.id
$currency = [uri]::EscapeDataString("Euro Zone-Euro")
Invoke-RestMethod "http://localhost:8080/transactions/$id`?currency=$currency"

# Open Swagger UI
Start-Process "http://localhost:8080/swagger-ui.html"
```

## Architecture Notes
- Controller, service, and repository layers keep API concerns, business logic, and persistence responsibilities separated.
- Treasury integration is isolated in `TreasuryApiClient`, decoupling external exchange-rate lookup from API and persistence code.
- Integration tests use H2 + Flyway test migrations and mock Treasury API calls for fast, repeatable verification.
- Operational values (datasource, treasury endpoint, timeouts) are externalized in `application.yml`.

## Assumptions
- Transaction dates are accepted as provided in the request.
- Treasury API availability is required for conversion responses.
- Exchange rates are selected from the latest eligible rate at or before transaction date, within a 6-month window.
