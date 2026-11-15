package org.sid.Notification_service.service;

import org.sid.Notification_service.dto.OrderCreatedEvent;
import org.sid.Notification_service.entity.ProcessedNotification;
import org.sid.Notification_service.repository.ProcessedNotificationRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OrderNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(OrderNotificationListener.class);
    private final ProcessedNotificationRepository repository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String recipient;
    private final String from;
    private final boolean mailEnabled;

    public OrderNotificationListener(ProcessedNotificationRepository repository, ObjectProvider<JavaMailSender> mailSenderProvider,
                                     @Value("${notification.mail.to:}") String recipient,
                                     @Value("${notification.mail.from:no-reply@example.com}") String from,
                                     @Value("${notification.mail.enabled:false}") boolean mailEnabled) {
        this.repository = repository;
        this.mailSenderProvider = mailSenderProvider;
        this.recipient = recipient;
        this.from = from;
        this.mailEnabled = mailEnabled;
    }

    @RabbitListener(queues = "notifications.order-created")
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {
        String eventId = "order-created-" + event.orderId();
        if (repository.existsById(eventId)) {
            log.info("Duplicate notification ignored for order {}", event.orderId());
            return;
        }
        String body = "Your order %d was received. Total: %s".formatted(event.orderId(), event.totalAmount());
        if (mailEnabled && !recipient.isBlank()) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipient);
            message.setSubject("Order confirmation #" + event.orderId());
            message.setText(body);
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) throw new IllegalStateException("SMTP is enabled but no mail sender is configured");
            mailSender.send(message);
            log.info("Confirmation email sent for order {} to {}", event.orderId(), recipient);
        } else {
            log.info("Notification received (SMTP disabled): {}", body);
        }
        repository.save(new ProcessedNotification(eventId, event.orderId()));
    }
}
