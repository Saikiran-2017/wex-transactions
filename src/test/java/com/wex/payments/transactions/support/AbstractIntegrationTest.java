package com.wex.payments.transactions.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    private enum DatabaseMode {
        TESTCONTAINERS,
        LOCAL_POSTGRES,
        H2
    }

    private static final DatabaseMode DATABASE_MODE;
    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER;
    static final WireMockServer WIRE_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIRE_MOCK.start();

        PostgreSQLContainer<?> container = null;
        DatabaseMode mode = DatabaseMode.H2;

        try {
            PostgreSQLContainer<?> candidate = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("wex_transactions")
                    .withUsername("wex_user")
                    .withPassword("wex_password");
            candidate.start();
            container = candidate;
            mode = DatabaseMode.TESTCONTAINERS;
            log.info("Integration tests using Testcontainers PostgreSQL on {}", candidate.getJdbcUrl());
        } catch (Exception ex) {
            log.warn("Testcontainers PostgreSQL unavailable: {}", ex.getMessage());
            if (isLocalPostgresAvailable()) {
                mode = DatabaseMode.LOCAL_POSTGRES;
                log.info("Integration tests using local PostgreSQL at localhost:5432");
            } else {
                mode = DatabaseMode.H2;
                log.info("Integration tests using in-memory H2 (PostgreSQL mode)");
            }
        }

        POSTGRES_CONTAINER = container;
        DATABASE_MODE = mode;
    }

    @BeforeAll
    static void configureWireMockClient() {
        WireMock.configureFor("localhost", WIRE_MOCK.port());
    }

    @AfterAll
    static void stopContainers() {
        if (POSTGRES_CONTAINER != null) {
            POSTGRES_CONTAINER.stop();
        }
        WIRE_MOCK.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        switch (DATABASE_MODE) {
            case TESTCONTAINERS -> {
                registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
                registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
                registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
                registry.add("spring.flyway.locations", () -> "classpath:db/migration");
            }
            case LOCAL_POSTGRES -> {
                registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/wex_transactions");
                registry.add("spring.datasource.username", () -> "wex_user");
                registry.add("spring.datasource.password", () -> "wex_password");
                registry.add("spring.flyway.locations", () -> "classpath:db/migration");
            }
            case H2 -> {
                registry.add("spring.datasource.url",
                        () -> "jdbc:h2:mem:wex_transactions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
                registry.add("spring.datasource.username", () -> "sa");
                registry.add("spring.datasource.password", () -> "");
                registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
                registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
                registry.add("spring.flyway.locations", () -> "classpath:db/test-migration");
            }
            default -> throw new IllegalStateException("Unsupported database mode: " + DATABASE_MODE);
        }

        registry.add("treasury.api.base-url", () -> WIRE_MOCK.baseUrl());
        registry.add("treasury.api.connect-timeout-ms", () -> 3000);
        registry.add("treasury.api.read-timeout-ms", () -> 5000);
    }

    @BeforeEach
    void resetWireMock() {
        WIRE_MOCK.resetAll();
        WireMock.configureFor("localhost", WIRE_MOCK.port());
    }

    private static boolean isLocalPostgresAvailable() {
        try {
            Connection connection = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/wex_transactions",
                    "wex_user",
                    "wex_password"
            );
            connection.close();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
