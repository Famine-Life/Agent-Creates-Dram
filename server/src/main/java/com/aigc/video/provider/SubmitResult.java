package com.aigc.video.provider;

/**
 * 供应商任务提交结果
 *
 * @param providerTaskId 供应商侧任务 ID，用于后续轮询
 */
public record SubmitResult(String providerTaskId) {
}
