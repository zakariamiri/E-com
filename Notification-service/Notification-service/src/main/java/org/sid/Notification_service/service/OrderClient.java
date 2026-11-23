package org.sid.Notification_service.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", path = "/api/orders")
public interface OrderClient {
    @GetMapping("/{id}")
    OrderCustomer getOrder(@PathVariable("id") Long orderId);

    record OrderCustomer(Long customerId) {}
}
