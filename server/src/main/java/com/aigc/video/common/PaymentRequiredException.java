package com.aigc.video.common;

/** 余额不足或会员权益不足。 */
public class PaymentRequiredException extends RuntimeException {

    /** 业务错误码，供前端区分额度不足、余额不足等情况 */
    private final String errorCode;

    public PaymentRequiredException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
