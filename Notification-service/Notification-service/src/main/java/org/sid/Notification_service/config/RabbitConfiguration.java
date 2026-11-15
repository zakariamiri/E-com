package org.sid.Notification_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

@Configuration
public class RabbitConfiguration {
    @Bean
    TopicExchange ecommerceExchange() {
        return new TopicExchange("ecommerce.events", true, false);
    }

    @Bean
    Queue orderCreatedQueue() {
        return new Queue("notifications.order-created", true);
    }

    @Bean
    Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(ecommerceExchange).with("order.created");
    }

    @Bean
    JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
