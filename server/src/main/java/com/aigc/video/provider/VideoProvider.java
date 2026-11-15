package com.aigc.video.provider;

/** 视频生成协议适配器抽象 */
public interface VideoProvider {

    /** 协议标识，与配置中的 protocols.video 对应，如 ark-video / mock-video */
    String name();

    /** 提交生成任务 */
    SubmitResult submit(SubmitCommand command);

    /** 轮询任务状态 */
    PollResult poll(String providerTaskId, VideoContext context);

    /** 取消平台侧任务；不支持取消的协议保持默认空实现 */
    default void cancel(String providerTaskId, VideoContext context) {
    }
}
