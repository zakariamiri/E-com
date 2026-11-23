package org.sid.Order_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.sid.Order_service.enums.PaymentMethod;
import java.util.List;

public record CreateOrderRequest(
        @NotNull @Positive Long customerId,
        @NotBlank String shippingAddress,
        @NotEmpty List<@Valid OrderItemRequest> items,
        PaymentMethod paymentMethod
) {
    public CreateOrderRequest(Long customerId, String shippingAddress, List<OrderItemRequest> items) {
        this(customerId, shippingAddress, items, PaymentMethod.CARD);
    }
}
