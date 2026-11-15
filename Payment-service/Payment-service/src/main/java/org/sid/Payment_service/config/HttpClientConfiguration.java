package org.sid.Payment_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
