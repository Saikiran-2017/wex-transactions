package com.wex.payments.transactions;

import com.wex.payments.transactions.config.TreasuryApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TreasuryApiProperties.class)
public class WexTransactionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WexTransactionsApplication.class, args);
    }
}
