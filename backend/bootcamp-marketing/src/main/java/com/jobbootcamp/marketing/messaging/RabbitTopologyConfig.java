package com.jobbootcamp.marketing.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "rabbit")
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange domainExchange(@Value("${app.outbox.exchange:bc.domain.v1}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue projectionQueue(@Value("${app.outbox.projection-queue:bc.marketing.projection.v1}") String queue) {
        return new Queue(queue, true, false, false);
    }

    @Bean
    public Binding projectionBinding(Queue projectionQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(projectionQueue).to(domainExchange)
                .with("marketing.projection-changed.v1");
    }
}