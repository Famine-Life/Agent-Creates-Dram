package com.aigc.video.common;

/** 本地任务队列达到容量上限。 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
