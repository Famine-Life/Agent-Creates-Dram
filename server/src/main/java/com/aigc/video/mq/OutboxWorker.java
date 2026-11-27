package com.aigc.video.mq;

import com.aigc.video.domain.EventOutbox;
import com.aigc.video.mapper.EventOutboxMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 消费 Outbox：本地放入唤醒队列，配置 RabbitMQ 时同时转发。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWorker {

    private static final int MAX_ATTEMPTS = 8;

    private final EventOutboxMapper outboxMapper;
    private final TaskWakeupQueue wakeupQueue;
    private final ObjectProvider<RabbitTaskEventPublisher> rabbitPublisher;

    /** 周期扫描待投递事件，投递本地唤醒队列与 RabbitMQ，失败按指数退避重试。 */
    @Scheduled(initialDelay = 3000, fixedDelayString = "${aigc.mq.outbox-interval-ms:1000}")
    public void tick() {
        List<EventOutbox> events = outboxMapper.selectList(Wrappers.<EventOutbox>lambdaQuery()
                .eq(EventOutbox::getStatus, EventOutbox.STATUS_PENDING)
                .le(EventOutbox::getNextAttemptAt, LocalDateTime.now())
                .orderByAsc(EventOutbox::getId)
                .last("LIMIT 50"));
        for (EventOutbox event : events) {
            int claimed = outboxMapper.update(null, Wrappers.<EventOutbox>lambdaUpdate()
                    .eq(EventOutbox::getId, event.getId())
                    .eq(EventOutbox::getStatus, EventOutbox.STATUS_PENDING)
                    .set(EventOutbox::getStatus, EventOutbox.STATUS_PROCESSING)
                    .set(EventOutbox::getUpdatedAt, LocalDateTime.now()));
            if (claimed == 0) {
                continue;
            }
            try {
                if ("generation_task".equals(event.getAggregateType())) {
                    wakeupQueue.offer(event.getAggregateId());
                }
                RabbitTaskEventPublisher publisher = rabbitPublisher.getIfAvailable();
                if (publisher != null) {
                    publisher.publish(event);
                }
                outboxMapper.update(null, Wrappers.<EventOutbox>lambdaUpdate()
                        .eq(EventOutbox::getId, event.getId())
                        .set(EventOutbox::getStatus, EventOutbox.STATUS_PROCESSED)
                        .set(EventOutbox::getUpdatedAt, LocalDateTime.now()));
            } catch (Exception e) {
                int attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
                boolean dead = attempts >= MAX_ATTEMPTS;
                outboxMapper.update(null, Wrappers.<EventOutbox>lambdaUpdate()
                        .eq(EventOutbox::getId, event.getId())
                        .set(EventOutbox::getAttempts, attempts)
                        .set(EventOutbox::getStatus, dead ? EventOutbox.STATUS_DEAD : EventOutbox.STATUS_PENDING)
                        .set(EventOutbox::getLastError, shorten(e.getMessage()))
                        .set(EventOutbox::getNextAttemptAt, LocalDateTime.now().plusSeconds(backoff(attempts)))
                        .set(EventOutbox::getUpdatedAt, LocalDateTime.now()));
                log.warn("Outbox 处理失败 eventId={} attempts={}", event.getEventId(), attempts);
            }
        }
    }

    private long backoff(int attempts) {
        return Math.min(300, (long) Math.pow(2, Math.min(attempts, 8)));
    }

    private String shorten(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
