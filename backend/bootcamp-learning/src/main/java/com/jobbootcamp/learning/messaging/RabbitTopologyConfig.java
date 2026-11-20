package com.jobbootcamp.learning.messaging;

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

    public static final String CLAIM_QUEUE = "bc.learning.course-free-claim.v1";
    public static final String COMPLETION_QUEUE = "bc.course.learning-free-claim.v1";
    public static final String ROUTE_QUEUE = "bc.learning.course-route-changed.v1";
    public static final String LESSON_QUEUE = "bc.marketing.learning-lesson.v1";
    public static final String ORDER_PAID_QUEUE = "bc.learning.order-paid.v1";

    @Bean
    public TopicExchange domainExchange(@Value("${app.outbox.exchange:bc.domain.v1}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue freeClaimQueue() {
        return new Queue(CLAIM_QUEUE, true, false, false);
    }

    @Bean
    public Queue freeClaimCompletionQueue() {
        return new Queue(COMPLETION_QUEUE, true, false, false);
    }

    @Bean
    public Queue routeChangedQueue() {
        return new Queue(ROUTE_QUEUE, true, false, false);
    }

    @Bean
    public Queue lessonCompletedQueue() {
        return new Queue(LESSON_QUEUE, true, false, false);
    }

    @Bean
    public Queue orderPaidQueue() {
        return new Queue(ORDER_PAID_QUEUE, true, false, false);
    }

    @Bean
    public Binding freeClaimBinding(Queue freeClaimQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(freeClaimQueue).to(domainExchange).with("course.free-claim-accepted.v1");
    }

    @Bean
    public Binding freeClaimCompletionBinding(Queue freeClaimCompletionQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(freeClaimCompletionQueue).to(domainExchange)
                .with("learning.free-claim-completed.v1");
    }

    @Bean
    public Binding routeChangedBinding(Queue routeChangedQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(routeChangedQueue).to(domainExchange).with("course.route-changed.v1");
    }

    @Bean
    public Binding lessonCompletedBinding(Queue lessonCompletedQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(lessonCompletedQueue).to(domainExchange).with("learning.lesson-completed.v1");
    }

    @Bean
    public Binding orderPaidBinding(Queue orderPaidQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(orderPaidQueue).to(domainExchange).with("trade.payment-confirmed.v1");
    }
}