package com.aigc.video.mq;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** 调度唤醒队列：Outbox / RabbitMQ 消费后优先提交对应任务。 */
@Component
public class TaskWakeupQueue {

    private static final int MAX = 2000;

    private final Set<Long> pending = new LinkedHashSet<>();

    /** 加入唤醒集合，超出上限时淘汰最早加入的任务。 */
    public synchronized void offer(Long taskId) {
        if (taskId == null) {
            return;
        }
        if (pending.size() >= MAX) {
            pending.remove(pending.iterator().next());
        }
        pending.add(taskId);
    }

    /** 取出一个待唤醒任务 ID，队列为空返回 null。 */
    public synchronized Long poll() {
        if (pending.isEmpty()) {
            return null;
        }
        Long first = pending.iterator().next();
        pending.remove(first);
        return first;
    }

    /** 取出并清空全部待唤醒任务。 */
    public synchronized Set<Long> drain() {
        Set<Long> copy = new LinkedHashSet<>(pending);
        pending.clear();
        return copy;
    }
}
