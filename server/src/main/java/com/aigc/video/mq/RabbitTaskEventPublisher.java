package com.aigc.video.mq;

import com.aigc.video.domain.EventOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** aigc.mq.type=rabbitmq 时把 Outbox 事件转发到消息队列。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aigc.mq", name = "type", havingValue = "rabbitmq")
public class RabbitTaskEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${aigc.mq.queue:aigc.task.dispatch}")
    private String queue;

    /** 将事件对应的聚合 ID 发送到任务队列。 */
    public void publish(EventOutbox event) {
        rabbitTemplate.convertAndSend(queue, String.valueOf(event.getAggregateId()));
    }
}
