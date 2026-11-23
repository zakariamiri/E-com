package org.sid.Payment_service.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentStatusChangedEvent(
        UUID eventId,
        String eventType,
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        String status,
        Instant occurredAt
) {
}