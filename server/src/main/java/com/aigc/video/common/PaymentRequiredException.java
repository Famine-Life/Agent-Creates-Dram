package com.aigc.video.common;

/** 余额不足或会员权益不足。 */
public class PaymentRequiredException extends RuntimeException {

    private final String errorCode;

    public PaymentRequiredException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
