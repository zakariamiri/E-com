package org.sid.Notification_service.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecovererWithConfirms;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AuthListenerConfiguration {
    @Bean
    Queue authFailedEventsQueue() {
        return new Queue("notifications.auth.failed", true);
    }

    @Bean
    SimpleRabbitListenerContainerFactory authListenerContainerFactory(
            ConnectionFactory connectionFactory, RabbitTemplate rabbitTemplate) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(true);
        var recoverer = new RepublishMessageRecovererWithConfirms(
                rabbitTemplate, "", "notifications.auth.failed",
                CachingConnectionFactory.ConfirmType.CORRELATED);
        recoverer.setConfirmTimeout(10000);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxRetries(2)
                .backOffOptions(1000, 2, 5000)
                .recoverer(recoverer)
                .build());
        return factory;
    }
}
