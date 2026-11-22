package org.sid.Order_service.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutCartRequest(@NotBlank String shippingAddress) {
}
