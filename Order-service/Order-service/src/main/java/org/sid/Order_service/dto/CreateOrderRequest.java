package org.sid.Order_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record CreateOrderRequest(
        @NotNull @Positive Long customerId,
        @NotBlank String shippingAddress,
        @NotEmpty List<@Valid OrderItemRequest> items
) {}
