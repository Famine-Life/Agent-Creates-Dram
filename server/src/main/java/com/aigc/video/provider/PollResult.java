package com.aigc.video.provider;

/**
 * 供应商任务查询结果
 *
 * @param status   QUEUED / RUNNING / SUCCEEDED / FAILED
 * @param progress 0-100
 * @param videoUrl 成片地址（成功时才有）
 * @param coverUrl 封面地址（可选）
 * @param error    失败原因
 */
public record PollResult(String status, int progress, String videoUrl, String coverUrl, String error) {

    public static PollResult running(int progress) {
        return new PollResult("RUNNING", progress, null, null, null);
    }

    public static PollResult succeeded(String videoUrl, String coverUrl) {
        return new PollResult("SUCCEEDED", 100, videoUrl, coverUrl, null);
    }

    public static PollResult failed(String error) {
        return new PollResult("FAILED", 0, null, null, error);
    }
}
