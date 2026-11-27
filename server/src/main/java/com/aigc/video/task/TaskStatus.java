package com.aigc.video.task;

/** 任务状态机：本地排队、提交中、平台排队、生成中与终态。 */
public enum TaskStatus {

    IDLE,
    RECEIVED,
    QUEUED,
    SUBMITTING,
    SUBMITTED,
    PROVIDER_QUEUED,
    RUNNING,
    SUBMIT_UNKNOWN,
    SUCCEEDED,
    FAILED,
    TIMEOUT,
    CANCELED,
    /** 积分不足，未提交供应商 */
    INSUFFICIENT_BALANCE,
    /** 当日额度耗尽，未提交供应商 */
    QUOTA_EXCEEDED;

    /** 未结束，可取消 */
    public boolean isActive() {
        return this == RECEIVED || this == QUEUED || this == SUBMITTING
                || this == SUBMITTED || this == PROVIDER_QUEUED || this == RUNNING;
    }

    public static boolean isActive(String status) {
        try {
            return valueOf(status).isActive();
        } catch (Exception e) {
            return false;
        }
    }
}
