package org.sid.Notification_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class AccountPaymentListener {

    private static final Logger log =
            LoggerFactory.getLogger(AccountPaymentListener.class);

    private final ObjectMapper json;
    private final AuthEventReceipt authReceipts;
    private final PaymentReceiptMailer paymentReceipts;

    public AccountPaymentListener(
            ObjectMapper json,
            AuthEventReceipt authReceipts,
            PaymentReceiptMailer paymentReceipts
    ) {
        this.json = json;
        this.authReceipts = authReceipts;
        this.paymentReceipts = paymentReceipts;
    }

    @Bean
    Queue accountCreatedNotificationQueue(
            @Value("${auth.events.user-created-queue:auth.user.created}")
            String name
    ) {
        return new Queue(name, true);
    }

    @Bean
    Queue paymentStatusNotificationQueue(
            @Value("${payment.events.queue:notifications.payment-status}")
            String name
    ) {
        return new Queue(name, true);
    }

    @RabbitListener(
            queues = "${auth.events.user-created-queue:auth.user.created}",
            containerFactory = "authListenerContainerFactory"
    )
    public void onUserCreated(Message message) {
        UserCreatedEvent event = json.readValue(
                message.getBody(),
                UserCreatedEvent.class
        );

        if (event.eventId() == null || event.userId() == null
                || !"USER_CREATED".equals(event.eventType())) {
            throw new IllegalArgumentException("Invalid USER_CREATED event");
        }
        String eventId = "auth-" + event.eventId();
        if (!authReceipts.record(eventId)) {
            log.info("Duplicate Auth event ignored: {}", event.eventId());
            return;
        }

        log.info(
                "Compte créé reçu pour l'utilisateur {}",
                event.userId()
        );

    }

    @RabbitListener(
            queues = "${payment.events.queue:notifications.payment-status}",
            containerFactory = "paymentListenerContainerFactory"
    )
    public void onPaymentChanged(Message message) {
        PaymentStatusChangedEvent event = json.readValue(
                message.getBody(),
                PaymentStatusChangedEvent.class
        );

        if (event.eventId() == null || event.paymentId() == null || event.paymentId() <= 0
                || event.orderId() == null || event.orderId() <= 0
                || event.amount() == null || event.amount().signum() <= 0
                || event.occurredAt() == null
                || !"PAYMENT_STATUS_CHANGED".equals(event.eventType())
                || !java.util.Set.of("SUCCEEDED", "FAILED").contains(
                        java.util.Objects.requireNonNullElse(event.status(), ""))) {
            throw new IllegalArgumentException("Invalid PAYMENT_STATUS_CHANGED event");
        }
        String eventId = "payment-" + event.eventId();
        boolean sendReceipt = "SUCCEEDED".equals(event.status()) && paymentReceipts.isEnabled();
        if (!authReceipts.recordAndProcess(eventId, event.orderId(), () -> {
            if (sendReceipt) {
                paymentReceipts.send(event);
            }
        })) {
            log.info("Duplicate Payment event ignored: {}", event.eventId());
            return;
        }

        log.info(
                "Paiement {} reçu pour la commande {} avec statut {}",
                event.paymentId(),
                event.orderId(),
                event.status()
        );
        if (sendReceipt) {
            log.info("Reçu envoyé pour le paiement {}", event.paymentId());
        }
    }

    public record UserCreatedEvent(
            UUID eventId,
            String eventType,
            Long userId,
            @com.fasterxml.jackson.annotation.JsonAlias("email")
            String recipientEmail,
            Instant occurredAt
    ) {
    }

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
}
