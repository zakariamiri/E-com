package org.sid.Auth_service.event;

import java.time.Instant;
import java.util.UUID;

public record UserCreatedEvent(
        UUID eventId,
        String eventType,
        Long userId,
        String recipientEmail,
        Instant occurredAt
) {
}
