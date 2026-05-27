package com.wex.payments.transactions.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transactionsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WEX Transactions API")
                        .description("Corporate Payments transaction processing APIs")
                        .version("v1"));
    }
}
