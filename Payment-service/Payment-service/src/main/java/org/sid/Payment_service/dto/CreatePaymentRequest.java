package org.sid.Payment_service.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull @Positive Long orderId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount
) {
}
