package org.sid.Auth_service.event;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_outbox_events", indexes =
        @Index(name = "idx_auth_outbox_pending", columnList = "sent_at,next_attempt_at"))
public class AuthOutboxEvent {
    @Id
    UUID id;
    @Column(nullable = false, columnDefinition = "text")
    String payload;
    @Column(nullable = false)
    Instant createdAt;
    @Column(name = "next_attempt_at", nullable = false)
    Instant nextAttemptAt;
    @Column(name = "sent_at")
    Instant sentAt;
    @Column(nullable = false)
    int attempts;

    protected AuthOutboxEvent() {
    }

    AuthOutboxEvent(UUID id, String payload) {
        this.id = id;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.nextAttemptAt = createdAt;
    }
}
