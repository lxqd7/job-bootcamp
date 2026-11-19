package com.jobbootcamp.marketing.messaging;

import com.jobbootcamp.contracts.event.EventEnvelope;

public interface EventTransport {

    void publish(EventEnvelope event) throws EventTransportException;

    String name();
}