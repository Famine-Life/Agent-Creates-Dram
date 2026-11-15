package com.aigc.video.task;

/** 任务状态机：IDLE(草稿) → QUEUED → SUBMITTED → RUNNING → SUCCEEDED / FAILED / TIMEOUT / CANCELED */
public enum TaskStatus {

    IDLE,
    RECEIVED,
    QUEUED,
    SUBMITTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMEOUT,
    CANCELED;

    /** 未结束，可取消 */
    public boolean isActive() {
        return this == RECEIVED || this == QUEUED || this == SUBMITTED || this == RUNNING;
    }

    public static boolean isActive(String status) {
        try {
            return valueOf(status).isActive();
        } catch (Exception e) {
            return false;
        }
    }
}
