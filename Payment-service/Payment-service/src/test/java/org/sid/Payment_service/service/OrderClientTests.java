package org.sid.Payment_service.service;

import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import feign.Retryer;
import feign.hc5.ApacheHttp5Client;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;
import org.sid.Payment_service.dto.OrderInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderClientTests {
    @Test
    void readsAndConfirmsOrdersOverHttpWithFeign() throws Exception {
        AtomicInteger unstableReads = new AtomicInteger();
        AtomicInteger unavailableConfirmations = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/orders", exchange -> {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                exchange.getResponseHeaders().set("Connection", "close");
                if (method.equals("GET") && path.equals("/api/orders/9") && unstableReads.incrementAndGet() == 1) {
                    exchange.sendResponseHeaders(503, -1);
                } else if (method.equals("GET") && (path.equals("/api/orders/7") || path.equals("/api/orders/9"))) {
                    byte[] body = """
                            {"id":7,"totalAmount":49.80,"status":"CREATED"}
                            """.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } else if (method.equals("PATCH") && path.equals("/api/orders/7/confirm")) {
                    exchange.sendResponseHeaders(204, -1);
                } else if (method.equals("PATCH") && path.equals("/api/orders/8/confirm")) {
                    exchange.sendResponseHeaders(409, -1);
                } else if (method.equals("PATCH") && path.equals("/api/orders/10/confirm")) {
                    unavailableConfirmations.incrementAndGet();
                    exchange.sendResponseHeaders(503, -1);
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            }
        });

        server.start();
        try (var http = HttpClients.custom().disableAutomaticRetries().build()) {
            var mapper = JsonMapper.builder().build();
            String path = OrderFeignClient.class.getAnnotation(FeignClient.class).path();
            OrderFeignClient api = Feign.builder()
                    .contract(new SpringMvcContract())
                    .client(new ApacheHttp5Client(http))
                    .retryer(Retryer.NEVER_RETRY)
                    .decoder((response, type) -> mapper.readValue(response.body().asInputStream(), OrderInfo.class))
                    .target(OrderFeignClient.class, "http://127.0.0.1:" + server.getAddress().getPort() + path);
            OrderClient client = new OrderClient(api);

            assertEquals(7L, client.getOrder(7L).id());
            client.confirmOrder(7L);
            assertEquals(404, assertThrows(ResponseStatusException.class,
                    () -> client.getOrder(8L)).getStatusCode().value());
            var conflict = assertThrows(ResponseStatusException.class, () -> client.confirmOrder(8L));
            assertEquals(409, conflict.getStatusCode().value(), () -> String.valueOf(conflict.getCause()));
            client.getOrder(9L);
            assertEquals(2, unstableReads.get());

            OrderClient clientDuringOutage = new OrderClient(api);
            for (int attempt = 0; attempt < 4; attempt++) {
                var unavailable = assertThrows(ResponseStatusException.class, () -> clientDuringOutage.confirmOrder(10L));
                assertEquals(503, unavailable.getStatusCode().value());
            }
            assertEquals(6, unavailableConfirmations.get());
            assertEquals(7L, clientDuringOutage.getOrder(7L).id());
        } finally {
            server.stop(0);
        }
    }
}
