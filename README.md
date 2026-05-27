# wex-transactions

Production-grade Spring Boot backend service scaffold for WEX Corporate Payments interview assignment.

## Tech Stack
- Java 17
- Spring Boot 3.2.x
- Maven
- PostgreSQL
- Spring Data JPA
- Flyway
- OpenAPI / Swagger
- WebClient
- JUnit 5, Mockito, TestContainers, WireMock

## Project Status
Initialization scaffold only. Business logic, entities, controllers, and tests are intentionally not implemented in this step.

## Prerequisites
- Java 17+
- Maven 3.9+
- Docker (for local PostgreSQL)

## Run PostgreSQL Locally
```bash
docker compose up -d
```

## Run Application
```bash
./mvnw spring-boot:run
```

## Package
```bash
./mvnw clean package
```

## API Documentation
Swagger UI will be available after implementation at:
- http://localhost:8080/swagger-ui/index.html

## Next Steps
- Add domain model and persistence mappings
- Implement layered business services
- Expose REST endpoints
- Add integration and contract tests
