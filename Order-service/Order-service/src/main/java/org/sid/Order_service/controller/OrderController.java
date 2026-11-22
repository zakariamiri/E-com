package org.sid.Order_service.controller;

import org.sid.Order_service.dto.OrderResponse;
import org.sid.Order_service.dto.CheckoutCartRequest;
import org.sid.Order_service.dto.CreateOrderRequest;
import org.sid.Order_service.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request, idempotencyKey);
    }

    @PostMapping("/from-cart/{customerId}")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrderFromCart(@PathVariable Long customerId,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @Valid @RequestBody CheckoutCartRequest request) {
        return orderService.createOrderFromCart(customerId, request.shippingAddress(), idempotencyKey);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id);
    }

    @GetMapping("/customer/{customerId}")
    public List<OrderResponse> getOrdersByCustomer(
            @PathVariable Long customerId) {
        return orderService.getOrdersByCustomerId(customerId);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable Long id) {
        return orderService.cancelOrder(id);
    }

    @PatchMapping("/{id}/confirm")
    public OrderResponse confirmOrder(@PathVariable Long id) {
        return orderService.confirmOrder(id);
    }
}
