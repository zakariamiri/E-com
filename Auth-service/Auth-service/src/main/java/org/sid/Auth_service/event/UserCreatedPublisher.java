package org.sid.Auth_service.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "auth.events.enabled", havingValue = "true")
public class UserCreatedPublisher {
    private static final Logger log = LoggerFactory.getLogger(UserCreatedPublisher.class);

    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final String queue;

    public UserCreatedPublisher(RabbitTemplate rabbit, ObjectMapper json,
                                @Value("${auth.events.user-created-queue}") String queue) {
        this.rabbit = rabbit;
        this.json = json;
        this.queue = queue;
    }

    @Bean
    Queue userCreatedQueue() {
        return new Queue(queue, true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(UserCreatedEvent event) {
        try {
            String eventId = event.eventId().toString();
            Message message = MessageBuilder.withBody(json.writeValueAsBytes(event))
                    .setContentType("application/json")
                    .setMessageId(eventId)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();

            CorrelationData correlation = new CorrelationData(eventId);
            rabbit.send("", queue, message, correlation);
            correlation.getFuture().orTimeout(10, java.util.concurrent.TimeUnit.SECONDS).whenComplete((confirm, error) -> {
                if (error != null || !confirm.ack() || correlation.getReturned() != null) {
                    log.error("RabbitMQ publication failed for event {}", eventId, error);
                    return;
                }
                log.info("RabbitMQ confirmed event {} on queue {}", eventId, queue);
            });
        } catch (RuntimeException exception) {
            log.error("Could not publish event {} after user creation", event.eventId(), exception);
        }
    }
}
