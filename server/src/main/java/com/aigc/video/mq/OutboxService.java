package com.aigc.video.mq;

import com.aigc.video.domain.EventOutbox;
import com.aigc.video.mapper.EventOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/** 事务 Outbox：事件与业务写入同一个事务，重启后可恢复。 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final EventOutboxMapper outboxMapper;

    /** 追加一条待投递事件，需与业务写入处于同一事务。 */
    public void append(String eventType, String aggregateType, Long aggregateId, Long userId, String payload) {
        EventOutbox event = new EventOutbox();
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setPayload(payload == null ? "{}" : payload);
        event.setStatus(EventOutbox.STATUS_PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now());
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        outboxMapper.insert(event);
    }
}
