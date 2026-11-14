package com.aigc.video.realtime;

/** 推送给前端画布的事件 */
public record CanvasEvent(String type, Object data) {

    public static CanvasEvent of(String type, Object data) {
        return new CanvasEvent(type, data);
    }
}
