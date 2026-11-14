package com.aigc.video.common;

/** 并发修改冲突（HTTP 409） */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
