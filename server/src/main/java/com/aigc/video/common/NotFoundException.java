package com.aigc.video.common;

/** 资源不存在，返回 404 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
