package org.sid.Notification_service.service;

import feign.FeignException;
import org.sid.Notification_service.dto.OrderCreatedEvent;
import org.sid.Notification_service.dto.UserResponse;
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
    private final AuthClient authClient;
    private final String recipient;
    private final String from;
    private final boolean mailEnabled;

    public OrderNotificationListener(ProcessedNotificationRepository repository, ObjectProvider<JavaMailSender> mailSenderProvider,
                                     AuthClient authClient,
                                     @Value("${notification.mail.to:}") String recipient,
                                     @Value("${notification.mail.from:no-reply@example.com}") String from,
                                     @Value("${notification.mail.enabled:false}") boolean mailEnabled) {
        this.repository = repository;
        this.mailSenderProvider = mailSenderProvider;
        this.authClient = authClient;
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
        ResolvedCustomer customer = resolveCustomer(event.customerId());
        String body = "Hello %s,\n\nYour order %d was received. Total: %s"
                .formatted(customer.name(), event.orderId(), event.totalAmount());
        String targetRecipient = customer.email();
        if (mailEnabled && !targetRecipient.isBlank()) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(targetRecipient);
            message.setSubject("Order confirmation #" + event.orderId());
            message.setText(body);
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) throw new IllegalStateException("SMTP is enabled but no mail sender is configured");
            mailSender.send(message);
            log.info("Confirmation email sent for order {} to {}", event.orderId(), targetRecipient);
        } else {
            log.info("Notification received (SMTP disabled): {}", body);
        }
        repository.save(new ProcessedNotification(eventId, event.orderId()));
    }

    private ResolvedCustomer resolveCustomer(Long customerId) {
        try {
            UserResponse customer = authClient.getUserById(customerId);
            if (!customer.enabled()) {
                log.warn("Skipping user email lookup for disabled account {}", customerId);
                return new ResolvedCustomer(recipient, "customer");
            }
            String firstName = customer.firstName() == null ? "" : customer.firstName().strip();
            String lastName = customer.lastName() == null ? "" : customer.lastName().strip();
            String fullName = (firstName + " " + lastName).trim();
            String name = fullName.isBlank() ? "customer" : fullName;
            String email = customer.email() == null ? recipient : customer.email().strip();
            return new ResolvedCustomer(email, name);
        } catch (FeignException.NotFound notFound) {
            log.warn("Customer {} not found in auth service", customerId);
            return new ResolvedCustomer(recipient, "customer");
        } catch (FeignException failure) {
            log.warn("Auth service unavailable while resolving customer {} email", customerId, failure);
            return new ResolvedCustomer(recipient, "customer");
        }
    }

    private record ResolvedCustomer(String email, String name) {
    }
}
