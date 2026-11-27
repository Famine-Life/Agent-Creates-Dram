package com.aigc.video.api;

import com.aigc.video.common.BadRequestException;
import com.aigc.video.common.ConflictException;
import com.aigc.video.common.NotFoundException;
import com.aigc.video.common.PaymentRequiredException;
import com.aigc.video.common.TooManyRequestsException;
import com.aigc.video.common.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * 提供应用的 HTTP 接口。
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> badRequest(BadRequestException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(ConflictException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, Object>> tooManyRequests(TooManyRequestsException e) {
        return body(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> unauthorized(UnauthorizedException e) {
        return body(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(PaymentRequiredException.class)
    public ResponseEntity<Map<String, Object>> paymentRequired(PaymentRequiredException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of("message", e.getMessage(), "errorCode", e.getErrorCode()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<Map<String, Object>> illegalArgument(Exception e) {
        String message = e instanceof MaxUploadSizeExceededException
                ? "文件超过大小限制"
                : e.getMessage();
        return body(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 客户端提前关闭连接（暂停/关闭视频、刷新页面、SSE 断开）时会触发 Broken pipe。
     * 响应已无法写回，只记录 debug 日志，避免被当成 500 接口异常刷屏。
     */
    @ExceptionHandler({ClientAbortException.class, AsyncRequestNotUsableException.class})
    public void clientDisconnected(Exception e) {
        log.debug("客户端已断开连接，忽略响应写入：{}", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        log.error("接口异常", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("message", message == null ? status.getReasonPhrase() : message));
    }
}
