package com.jobbootcamp.marketing.messaging;

import com.jobbootcamp.contracts.event.EventEnvelope;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "rabbit")
public class RabbitEventTransport implements EventTransport {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public RabbitEventTransport(RabbitTemplate rabbitTemplate,
                                @Value("${app.outbox.exchange:bc.domain.v1}") String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @Override
    public void publish(EventEnvelope event) throws EventTransportException {
        Message message = MessageBuilder.withBody(event.payloadJson().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setMessageId(event.eventId())
                .setType(event.type())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlation = new CorrelationData(event.eventId());
        try {
            rabbitTemplate.send(exchange, event.type(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new EventTransportException("PUBLISH_NACK", "RabbitMQ 未确认消息");
            }
        } catch (EventTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new EventTransportException("PUBLISH_FAILED", "RabbitMQ 投递失败", e);
        }
    }

    @Override
    public String name() {
        return "rabbit";
    }
}