package com.aigc.video.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 消费 RabbitMQ 任务事件，放入本地唤醒队列，与 DB Outbox 共用同一提交路径。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aigc.mq", name = "type", havingValue = "rabbitmq")
public class RabbitTaskEventConsumer {

    private final TaskWakeupQueue wakeupQueue;

    /** 收到任务 ID 后放入本地唤醒队列；非法消息直接丢弃以免阻塞队列。 */
    @RabbitListener(queues = "${aigc.mq.queue:aigc.task.dispatch}")
    public void onMessage(String taskId) {
        try {
            wakeupQueue.offer(Long.valueOf(taskId.trim()));
        } catch (NumberFormatException ignored) {
            // 非法消息直接丢弃，避免阻塞队列
        }
    }
}
