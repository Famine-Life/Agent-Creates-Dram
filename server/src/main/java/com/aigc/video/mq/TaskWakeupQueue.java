package com.aigc.video.mq;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** 调度唤醒队列：Outbox / RabbitMQ 消费后优先提交对应任务。 */
@Component
public class TaskWakeupQueue {

    private static final int MAX = 2000;

    private final Set<Long> pending = new LinkedHashSet<>();

    public synchronized void offer(Long taskId) {
        if (taskId == null) {
            return;
        }
        if (pending.size() >= MAX) {
            pending.remove(pending.iterator().next());
        }
        pending.add(taskId);
    }

    public synchronized Long poll() {
        if (pending.isEmpty()) {
            return null;
        }
        Long first = pending.iterator().next();
        pending.remove(first);
        return first;
    }

    public synchronized Set<Long> drain() {
        Set<Long> copy = new LinkedHashSet<>(pending);
        pending.clear();
        return copy;
    }
}
