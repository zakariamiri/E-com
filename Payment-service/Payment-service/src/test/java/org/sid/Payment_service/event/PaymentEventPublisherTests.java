package org.sid.Payment_service.event;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PaymentEventPublisherTests {
    @Test
    void retainsFailedEventAndRetriesWithTheSameId() {
        PaymentOutboxEvent row = new PaymentOutboxEvent(UUID.randomUUID(), "{}");
        AtomicInteger queries = new AtomicInteger();
        Query query = (Query) Proxy.newProxyInstance(Query.class.getClassLoader(),
                new Class<?>[]{Query.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getResultList")) {
                        return queries.incrementAndGet() <= 2 ? List.of(row) : List.of();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        EntityManager em = (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(), new Class<?>[]{EntityManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("createNativeQuery")) {
                        return query;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        RabbitTemplate rabbit = new RabbitTemplate() {
            private int attempts;
            @Override
            public <T> T execute(ChannelCallback<T> action) {
                return null;
            }
            @Override
            public void send(String exchange, String routingKey, Message message,
                             CorrelationData correlation) {
                if (++attempts == 1) {
                    throw new IllegalStateException("broker offline");
                }
                assertEquals(row.id.toString(), message.getMessageProperties().getMessageId());
                correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            }
        };
        PlatformTransactionManager manager = new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }
            public void commit(TransactionStatus status) {
            }
            public void rollback(TransactionStatus status) {
            }
        };
        PaymentEventPublisher publisher = new PaymentEventPublisher(
                rabbit, JsonMapper.builder().build(), em, manager, "notifications.payment-status");
        publisher.deliverPendingEvents();
        assertNull(row.sentAt);
        assertEquals(1, row.attempts);
        assertTrue(row.nextAttemptAt.isAfter(Instant.now()));
        publisher.deliverPendingEvents();
        assertNotNull(row.sentAt);
        assertEquals(2, row.attempts);
    }
}
