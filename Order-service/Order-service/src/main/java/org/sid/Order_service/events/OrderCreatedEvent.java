package org.sid.Order_service.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderCreatedEvent(Long orderId, Long customerId, BigDecimal totalAmount,
                                String shippingAddress, LocalDateTime createdAt, List<Item> items) {
    public record Item(Long productId, String productName, Integer quantity, BigDecimal unitPrice) {}
}
