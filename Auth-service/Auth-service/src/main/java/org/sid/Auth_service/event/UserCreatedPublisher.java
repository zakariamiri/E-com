package org.sid.Auth_service.event;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "auth.events.enabled", havingValue = "true")
public class UserCreatedPublisher {
    private static final Logger log = LoggerFactory.getLogger(UserCreatedPublisher.class);
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;
    private final String queue;

    public UserCreatedPublisher(RabbitTemplate rabbit, ObjectMapper json,
                                EntityManager entityManager,
                                PlatformTransactionManager transactionManager,
                                @Value("${auth.events.user-created-queue}") String queue) {
        this.rabbit = rabbit;
        this.json = json;
        this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
        this.queue = queue;
    }

    @Bean
    Queue userCreatedQueue() {
        return new Queue(queue, true);
    }

    // Saves the event atomically with the new user.
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UserCreatedEvent event) {
        entityManager.persist(new AuthOutboxEvent(
                event.eventId(), json.writeValueAsString(event)));
    }

    @Scheduled(fixedDelayString = "${auth.events.outbox.poll-delay-ms:5000}")
    public void deliverPendingEvents() {
        for (int count = 0; count < 20; count++) {
            Boolean delivered = transactions.execute(status -> deliverNext());
            if (!Boolean.TRUE.equals(delivered)) {
                return;
            }
        }
    }

    private boolean deliverNext() {
        // PostgreSQL row locks coordinate concurrent Auth instances.
        var pending = entityManager.createNativeQuery("""
                SELECT * FROM auth_outbox_events
                WHERE sent_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY created_at, id
                LIMIT 1 FOR UPDATE SKIP LOCKED
                """, AuthOutboxEvent.class).getResultList();
        if (pending.isEmpty()) {
            return false;
        }
        AuthOutboxEvent event = (AuthOutboxEvent) pending.get(0);
        event.attempts++;
        try {
            rabbit.execute(channel -> {
                channel.queueDeclare(queue, true, false, false, null);
                return null;
            });
            Message message = MessageBuilder
                    .withBody(event.payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .setContentType("application/json")
                    .setMessageId(event.id.toString())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();
            CorrelationData correlation = new CorrelationData(event.id.toString());
            rabbit.send("", queue, message, correlation);
            var confirmation = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirmation.ack() || correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ rejected or returned the event");
            }
            event.sentAt = Instant.now();
            log.info("RabbitMQ confirmed event {} on queue {}", event.id, queue);
            return true;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            scheduleRetry(event, failure);
            return false;
        } catch (Exception failure) {
            scheduleRetry(event, failure);
            return false;
        }
    }

    private void scheduleRetry(AuthOutboxEvent event, Exception failure) {
        long delaySeconds = Math.min(300L, 5L * (1L << Math.min(event.attempts - 1, 6)));
        event.nextAttemptAt = Instant.now().plusSeconds(delaySeconds);
        log.warn("Event {} retained in outbox after attempt {}, retry in {} seconds",
                event.id, event.attempts, delaySeconds, failure);
    }
}
