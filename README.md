# WEX Corporate Payments - Purchase Transaction Management System

## Overview
This service stores USD purchase transactions and retrieves converted transaction amounts using U.S. Treasury Reporting Rates of Exchange.
It is implemented as a production-style Spring Boot microservice with layered architecture, externalized configuration, migrations, and automated test coverage.

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

Example response:
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

Example response:
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

## Running Locally

Using Maven:
```powershell
.\mvnw.cmd spring-boot:run
```

Using Docker:
```bash
docker-compose up --build
```

## Running Tests

```powershell
.\mvnw.cmd test
```

## Swagger
[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

OpenAPI JSON:
[http://localhost:8080/api-docs](http://localhost:8080/api-docs)

## Architecture Notes
- Controller, service, and repository layers keep API concerns, business logic, and persistence responsibilities separated.
- Treasury integration is isolated in a dedicated client (`TreasuryApiClient`) so conversion lookup is decoupled from API and persistence code.
- Integration tests run with H2 + Flyway in test scope while mocking the external Treasury dependency.
- Operational and environment-specific values (datasource, treasury endpoint, timeouts) are externalized in configuration.

## Assumptions
- Transaction dates are accepted as provided in the request.
- Treasury API availability is required for conversion responses.
- Exchange rates are selected from the latest eligible rate at or before transaction date, within a 6-month window.
