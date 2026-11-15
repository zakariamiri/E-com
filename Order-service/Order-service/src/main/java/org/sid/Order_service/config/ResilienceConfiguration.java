package org.sid.Order_service.config;

import java.time.Duration;
import java.util.function.Consumer;

import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfiguration {
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> productCallsTimeLimiter() {
        return factory -> {
            TimeLimiterConfig timeout = TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofSeconds(10))
                    .cancelRunningFuture(true)
                    .build();
            Consumer<Resilience4JConfigBuilder> productCallConfig = builder -> builder
                    .circuitBreakerConfig(factory.getCircuitBreakerRegistry().getDefaultConfig())
                    .timeLimiterConfig(timeout);
            factory.configure(productCallConfig,
                    "ProductClientgetProductLong",
                    "ProductClientreserveLongStockRequest",
                    "ProductClientreleaseLongStockRequest");
        };
    }
}
