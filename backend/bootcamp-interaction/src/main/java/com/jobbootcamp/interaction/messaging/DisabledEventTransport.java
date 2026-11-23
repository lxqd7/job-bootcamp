package com.jobbootcamp.interaction.messaging;

import com.jobbootcamp.contracts.event.EventEnvelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "none", matchIfMissing = true)
public class DisabledEventTransport implements EventTransport {

    @Override
    public void publish(EventEnvelope event) throws EventTransportException {
        throw new EventTransportException("TRANSPORT_DISABLED", "未配置消息传输");
    }

    @Override
    public String name() {
        return "none";
    }
}