# WEX Corporate Payments - Purchase Transaction API

## Project Overview

API-only Spring Boot service for the WEX Corporate Payments assignment. It stores USD purchase transactions in PostgreSQL and retrieves converted amounts using the [U.S. Treasury Reporting Rates of Exchange API](https://fiscaldata.treasury.gov/api/v1/accounting/od/rates_of_exchange).

## Requirement Coverage

| Requirement | Implementation |
|---|---|
| Store purchase transaction (description, date, USD amount) | `POST /transactions` |
| Assign unique identifier | UUID generated on persist |
| Description max 50, not blank | Bean Validation + DB constraint |
| Valid transaction date | ISO `YYYY-MM-DD` parsing + validation |
| Positive purchase amount, HALF_UP to cents | Service rounding + `@Positive` |
| Sub-cent input accepted and rounded (e.g. `100.005` → `100.01`) | `TransactionService#createTransaction` |
| Persist across restarts | PostgreSQL + Flyway |
| Retrieve transaction in target currency | `GET /transactions/{id}?currency=...` |
| Treasury rate ≤ transaction date, within 6 months | `TreasuryApiClient` filter + `sort=-record_date` |
| No eligible rate → 422 | `CurrencyConversionException` |
| Converted amount HALF_UP to 2 decimals | `TransactionService#getTransactionInCurrency` |
| Consistent error contract | `ErrorResponse` + `ApiExceptionHandler` |

## Tech Stack

- Java 17
- Spring Boot 3.2.x
- Maven (wrapper included)
- PostgreSQL + Flyway
- Spring Data JPA
- Bean Validation
- WebClient (Treasury API)
- springdoc OpenAPI / Swagger UI
- Spring Boot Actuator
- JUnit 5, Mockito, AssertJ
- Testcontainers (PostgreSQL integration tests)
- WireMock (Treasury API tests)

## Architecture

```
Controller  →  Service  →  Repository  →  PostgreSQL
                  ↓
           TreasuryApiClient  →  U.S. Treasury API
```

- **Controller**: HTTP mapping, validation trigger, status codes only
- **Service**: Business rules (rounding, conversion, not-found)
- **Repository**: Persistence
- **TreasuryApiClient**: External exchange-rate lookup isolated from API/persistence

Base package: `com.wex.payments.transactions`

## Prerequisites

- **Java 17** (required; project targets release 17)
- Docker Desktop (optional, for PostgreSQL or full stack)

## Setup

### PostgreSQL with Docker

```powershell
docker compose up -d postgres
```

Default credentials (local/docker-compose):

- Database: `wex_transactions`
- User: `wex_user`
- Password: `wex_password`
- Port: `5432`

### Compile

```powershell
.\mvnw.cmd clean compile
```

### Run tests

```powershell
.\mvnw.cmd clean test
```

Integration tests use **WireMock** for the Treasury API. PostgreSQL is started via **Testcontainers** when the Docker Java client is available; otherwise tests fall back to local `docker compose` Postgres on port `5432`, then in-memory H2.

### Run application

```powershell
.\mvnw.cmd spring-boot:run
```

Or full stack (app + Postgres):

```powershell
docker compose up --build
```

Application: `http://localhost:8080`

## API Examples

### POST /transactions

```bash
curl -X POST http://localhost:8080/transactions ^
  -H "Content-Type: application/json" ^
  -d "{\"description\":\"Office supplies\",\"transactionDate\":\"2024-10-15\",\"purchaseAmount\":149.99}"
```

**201 Created**

```json
{
  "id": "b532f5f0-3f57-4604-9eea-73f1f9d70484",
  "description": "Office supplies",
  "transactionDate": "2024-10-15",
  "purchaseAmount": 149.99
}
```

### GET /transactions/{id}?currency=Euro%20Zone-Euro

```bash
curl "http://localhost:8080/transactions/b532f5f0-3f57-4604-9eea-73f1f9d70484?currency=Euro%%20Zone-Euro"
```

**200 OK**

```json
{
  "id": "b532f5f0-3f57-4604-9eea-73f1f9d70484",
  "description": "Office supplies",
  "transactionDate": "2024-10-15",
  "originalAmountUsd": 149.99,
  "exchangeRate": 0.924,
  "convertedAmount": 138.59,
  "currency": "Euro Zone-Euro"
}
```

## Swagger & Health

| Resource | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |
| Health | http://localhost:8080/actuator/health |

## Error Response Format

All API errors return:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Description is required.",
  "timestamp": "2026-05-27T21:00:00Z"
}
```

| Condition | HTTP Status |
|---|---|
| Validation / malformed JSON / invalid date / missing currency param | 400 |
| Transaction not found | 404 |
| No exchange rate / unsupported currency | 422 |
| Treasury API failure / timeout | 503 |
| Unexpected server error | 500 |

## Testing Strategy

| Layer | Class | Approach |
|---|---|---|
| Unit | `TransactionServiceTest` | Mockito: create, round, trim, not-found, conversion math |
| Unit | `TreasuryApiClientTest` | WireMock: query params, 6-month window, empty data, HTTP errors, timeout |
| Integration | `TransactionControllerIntegrationTest` | Testcontainers PostgreSQL + WireMock + MockMvc: full REST contract |

## Assumptions

- Purchase amounts are stored and returned in USD.
- Treasury `country_currency_desc` values must match Treasury API labels exactly (e.g. `Euro Zone-Euro`).
- Exchange rate selection uses the most recent eligible rate on or before the transaction date within the prior 6 calendar months.
- Amounts that round to zero cents (e.g. `0.001`) are rejected with `400` before persistence.
- Live Treasury API availability is required for manual conversion testing outside WireMock-backed tests.
