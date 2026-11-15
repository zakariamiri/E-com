package org.sid.Order_service.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

@Configuration
public class RabbitConfiguration {
    public static final String EXCHANGE = "ecommerce.events";
    public static final String ORDER_CREATED = "order.created";

    @Bean
    TopicExchange ecommerceExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
