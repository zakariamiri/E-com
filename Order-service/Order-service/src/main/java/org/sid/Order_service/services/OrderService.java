
package org.sid.Order_service.services;

import org.sid.Order_service.dto.OrderResponse;
import org.sid.Order_service.dto.CreateOrderRequest;
import org.sid.Order_service.dto.ProductInfo;
import org.sid.Order_service.entities.Order;
import org.sid.Order_service.entities.OrderItem;
import org.sid.Order_service.enums.OrderStatus;
import org.sid.Order_service.repositories.OrderRepository;
import org.sid.Order_service.events.OrderCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository orderRepository, ProductClient productClient, ApplicationEventPublisher events) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.events = events;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 1 to 100 characters");
        }
        var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return mapToResponse(existing.get());
        Set<Long> productIds = new HashSet<>();
        List<OrderItem> items = new ArrayList<>();
        List<OrderItemRequestWithId> reserved = new ArrayList<>();
        try {
            for (var requested : request.items()) {
                if (!productIds.add(requested.productId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Each product may appear only once in an order");
                }
                ProductInfo product = productClient.getProduct(requested.productId());
                if (product == null || !product.available()
                        || product.stockQuantity() < requested.quantity()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Product is unavailable or has insufficient stock: " + requested.productId());
                }
                productClient.reserve(product.id(), new org.sid.Order_service.dto.StockRequest(requested.quantity()));
                reserved.add(new OrderItemRequestWithId(product.id(), requested.quantity()));
                items.add(new OrderItem(product.price(), requested.quantity(), product.name(), product.id()));
            }

            Order order = new Order();
            order.setCustomerId(request.customerId());
            order.setIdempotencyKey(idempotencyKey);
            order.setShippingAddress(request.shippingAddress().strip());
            order.setStatus(OrderStatus.CREATED);
            items.forEach(order::addItem);
            order.setTotalAmount(order.getItems().stream().map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            Order saved = orderRepository.save(order);
            events.publishEvent(new OrderCreatedEvent(saved.getId(), saved.getCustomerId(), saved.getTotalAmount(),
                    saved.getShippingAddress(), saved.getCreatedAt(), saved.getItems().stream()
                    .map(item -> new OrderCreatedEvent.Item(item.getProductId(), item.getProductName(),
                            item.getQuantity(), item.getUnitPrice())).toList()));
            return mapToResponse(saved);
        } catch (RuntimeException failure) {
            releaseReservations(reserved, failure);
            throw failure;
        }
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> orderNotFound(orderId));
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a created order can be cancelled");
        }
        List<OrderItem> released = new ArrayList<>();
        try {
            for (OrderItem item : order.getItems()) {
                productClient.release(item.getProductId(), new org.sid.Order_service.dto.StockRequest(item.getQuantity()));
                released.add(item);
            }
        } catch (RuntimeException failure) {
            for (int i = released.size() - 1; i >= 0; i--) {
                OrderItem item = released.get(i);
                try {
                    productClient.reserve(item.getProductId(), new org.sid.Order_service.dto.StockRequest(item.getQuantity()));
                } catch (RuntimeException compensationFailure) {
                    failure.addSuppressed(compensationFailure);
                }
            }
            throw failure;
        }
        order.setStatus(OrderStatus.CANCELLED);
        return mapToResponse(order);
    }

    @Transactional
    public OrderResponse confirmOrder(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> orderNotFound(orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A cancelled order cannot be confirmed");
        }
        if (order.getStatus() == OrderStatus.CREATED) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
        return mapToResponse(order);
    }

    private void releaseReservations(List<OrderItemRequestWithId> reserved, RuntimeException failure) {
        for (int i = reserved.size() - 1; i >= 0; i--) {
            OrderItemRequestWithId item = reserved.get(i);
            try {
                productClient.release(item.productId(), new org.sid.Order_service.dto.StockRequest(item.quantity()));
            } catch (RuntimeException compensationFailure) {
                failure.addSuppressed(compensationFailure);
            }
        }
    }

    private record OrderItemRequestWithId(Long productId, int quantity) {}

    private ResponseStatusException orderNotFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with ID: " + id);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> orderNotFound(orderId));

        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomerId(Long customerId) {
        return orderRepository
                .findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private OrderResponse mapToResponse(Order order) {

        List<OrderResponse.OrderItemResponse> items =
                order.getItems().stream()
                        .map(this::mapItemToResponse)
                        .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getShippingAddress(),
                order.getCreatedAt(),
                items
        );
    }

    private OrderResponse.OrderItemResponse mapItemToResponse(
            OrderItem item
    ) {
        return new OrderResponse.OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
        );
    }
}
