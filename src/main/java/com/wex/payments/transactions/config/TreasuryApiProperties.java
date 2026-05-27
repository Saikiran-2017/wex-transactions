package com.wex.payments.transactions.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "treasury.api")
public class TreasuryApiProperties {

    private String baseUrl = "https://fiscaldata.treasury.gov/api/v1/accounting/od/rates_of_exchange";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
}
