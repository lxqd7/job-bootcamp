package com.jobbootcamp.assignment.messaging;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 提交事务提交后才发送唤醒，避免消费者先于任务可见。
 */
@Component
public class AiTaskQueuedNotifier {

    private final ObjectProvider<AiWakeupPublisher> publisher;

    public AiTaskQueuedNotifier(ObjectProvider<AiWakeupPublisher> publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQueued(AiTaskQueuedEvent event) {
        publisher.ifAvailable(value ->
                value.publish(event.taskId(), event.submissionId(), event.userId()));
    }
}