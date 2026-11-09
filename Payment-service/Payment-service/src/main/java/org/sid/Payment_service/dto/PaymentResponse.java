package org.sid.Payment_service.dto;

import org.sid.Payment_service.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        String transactionReference,
        Instant createdAt
) {
}
