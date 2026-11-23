package org.sid.Payment_service.service;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.sid.Payment_service.dto.OrderInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class OrderClient {
    private static final CircuitBreakerConfig BREAKER_CONFIG = CircuitBreakerConfig.custom()
            .slidingWindowSize(6)
            .minimumNumberOfCalls(3)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(5))
            .permittedNumberOfCallsInHalfOpenState(1)
            .ignoreException(failure -> failure instanceof FeignException feign && !isTemporary(feign))
            .build();
    private static final RetryConfig RETRY_CONFIG = RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ofMillis(200))
            .retryOnException(failure -> failure instanceof FeignException feign && isTemporary(feign))
            .build();

    private final OrderFeignClient client;
    private final CircuitBreaker readBreaker = CircuitBreaker.of("payment-read-order", BREAKER_CONFIG);
    private final CircuitBreaker confirmBreaker = CircuitBreaker.of("payment-confirm-order", BREAKER_CONFIG);
    private final Retry retry = Retry.of("payment-order", RETRY_CONFIG);

    public OrderClient(OrderFeignClient client) {
        this.client = client;
    }

    public OrderInfo getOrder(Long orderId) {
        return callOrder(readBreaker, () -> client.getOrder(orderId));
    }

    public void confirmOrder(Long orderId) {
        // Order re-applies confirmation safely if the first response was lost.
        callOrder(confirmBreaker, () -> {
            client.confirmOrder(orderId);
            return null;
        });
    }

    private <T> T callOrder(CircuitBreaker breaker, Supplier<T> operation) {
        try {
            return breaker.executeSupplier(() -> retry.executeSupplier(operation));
        } catch (CallNotPermittedException ex) {
            throw orderUnavailable(ex);
        } catch (FeignException ex) {
            if (isTemporary(ex)) throw orderUnavailable(ex);
            HttpStatus status = switch (ex.status()) {
                case 404 -> HttpStatus.NOT_FOUND;
                case 409 -> HttpStatus.CONFLICT;
                default -> HttpStatus.BAD_GATEWAY;
            };
            throw new ResponseStatusException(status, "OrderService a refusé la demande de paiement.", ex);
        }
    }

    private static boolean isTemporary(FeignException failure) {
        int status = failure.status();
        return status < 0 || status == 429 || status >= 500;
    }

    private static ResponseStatusException orderUnavailable(RuntimeException cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "OrderService est temporairement indisponible. Réessayez le même paiement.", cause);
    }
}
