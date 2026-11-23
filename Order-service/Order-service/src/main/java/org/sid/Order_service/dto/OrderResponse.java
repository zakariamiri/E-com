package org.sid.Order_service.dto;

import org.sid.Order_service.enums.OrderStatus;
import org.sid.Order_service.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        BigDecimal totalAmount,
        OrderStatus status,
        String shippingAddress,
        LocalDateTime createdAt,
        List<OrderItemResponse> items,
        PaymentMethod paymentMethod
) {
    public record OrderItemResponse(
            Long id,
            Long productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}
}
