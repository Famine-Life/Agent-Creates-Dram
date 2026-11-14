package com.aigc.video.common;

/** 参数错误，返回 400 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
