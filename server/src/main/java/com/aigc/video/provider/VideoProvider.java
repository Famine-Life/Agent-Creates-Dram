package com.aigc.video.provider;

/** 视频生成供应商抽象 */
public interface VideoProvider {

    /** 供应商标识，与 generation_task.provider 对应 */
    String name();

    /** 提交生成任务 */
    SubmitResult submit(SubmitCommand command);

    /** 轮询任务状态 */
    PollResult poll(String providerTaskId);
}
