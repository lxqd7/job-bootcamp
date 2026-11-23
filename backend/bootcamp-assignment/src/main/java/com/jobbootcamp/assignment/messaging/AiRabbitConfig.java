package com.jobbootcamp.assignment.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 唤醒队列只用于降低点评启动延迟；任务表仍是事实源，丢失消息由扫描兜底。
 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "app.ai.wakeup-enabled", havingValue = "true")
public class AiRabbitConfig {

    @Bean
    public TopicExchange aiWakeupExchange(@Value("${app.ai.wakeup.exchange:bc.domain.v1}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue aiWakeupQueue(@Value("${app.ai.wakeup.queue:bc.assignment.review-requested.v1}") String queue) {
        return new Queue(queue, true, false, false);
    }

    @Bean
    public Binding aiWakeupBinding(Queue aiWakeupQueue, TopicExchange aiWakeupExchange,
                                   @Value("${app.ai.wakeup.routing-key:assignment.review-requested.v1}") String routingKey) {
        return BindingBuilder.bind(aiWakeupQueue).to(aiWakeupExchange).with(routingKey);
    }
}