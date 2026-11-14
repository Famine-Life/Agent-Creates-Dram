package com.aigc.video.realtime;

/** 画布事件跨实例中继；返回 false 表示中继不可用，调用方回退本地推送 */
public interface CanvasEventRelay {

    boolean publish(Long workspaceId, CanvasEvent event);
}
