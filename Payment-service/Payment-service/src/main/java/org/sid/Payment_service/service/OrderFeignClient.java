package org.sid.Payment_service.service;

import org.sid.Payment_service.dto.OrderInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", url = "${services.order.base-url:http://localhost:8084}",
        path = "/api/orders")
public interface OrderFeignClient {
    @GetMapping("/{id}")
    OrderInfo getOrder(@PathVariable("id") Long orderId);

    @PatchMapping("/{id}/confirm")
    void confirmOrder(@PathVariable("id") Long orderId);
}
