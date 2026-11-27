package com.aigc.video.common;

/** 未登录或登录态失效。 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
