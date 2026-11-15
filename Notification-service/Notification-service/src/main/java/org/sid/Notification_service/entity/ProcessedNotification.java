package org.sid.Notification_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_notifications")
public class ProcessedNotification {
    @Id
    private String eventId;
    private Long orderId;
    private Instant processedAt;

    protected ProcessedNotification() {}

    public ProcessedNotification(String eventId, Long orderId) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.processedAt = Instant.now();
    }
}
