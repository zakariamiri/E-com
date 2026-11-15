package org.sid.Payment_service.service;

import org.sid.Payment_service.dto.OrderInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderClient {
    private final RestClient client;

    public OrderClient(RestClient.Builder builder,
                       @Value("${services.order.base-url:http://localhost:8084}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public OrderInfo getOrder(Long orderId) {
        try {
            return client.get().uri("/api/orders/{id}", orderId)
                    .retrieve().body(OrderInfo.class);
        } catch (RestClientResponseException ex) {
            HttpStatus status = ex.getStatusCode().value() == 404
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, "Could not retrieve order from Order-service", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Order-service is unavailable", ex);
        }
    }

    public void confirmOrder(Long orderId) {
        try {
            client.patch().uri("/api/orders/{id}/confirm", orderId)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            HttpStatus status = ex.getStatusCode().value() == 409
                    ? HttpStatus.CONFLICT : ex.getStatusCode().value() == 404
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, "Could not confirm order in Order-service", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Order-service is unavailable", ex);
        }
    }
}
