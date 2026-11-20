package com.jobbootcamp.trade.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "rabbit")
public class RabbitTopologyConfig {

    public static final String ORDER_PAID_QUEUE = "bc.learning.order-paid.v1";
    public static final String GRANT_ACK_QUEUE = "bc.trade.learning-order-granted.v1";

    @Bean
    public TopicExchange domainExchange(@Value("${app.outbox.exchange:bc.domain.v1}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue orderPaidQueue() {
        return new Queue(ORDER_PAID_QUEUE, true, false, false);
    }

    @Bean
    public Queue orderGrantedQueue() {
        return new Queue(GRANT_ACK_QUEUE, true, false, false);
    }

    @Bean
    public Binding orderPaidBinding(Queue orderPaidQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(orderPaidQueue).to(domainExchange).with("trade.payment-confirmed.v1");
    }

    @Bean
    public Binding orderGrantedBinding(Queue orderGrantedQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(orderGrantedQueue).to(domainExchange).with("learning.order-granted.v1");
    }
}