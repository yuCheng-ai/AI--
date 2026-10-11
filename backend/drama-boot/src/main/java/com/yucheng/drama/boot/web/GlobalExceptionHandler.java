package com.yucheng.drama.boot.web;

import com.yucheng.drama.kernel.api.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformedRequest(Exception e) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "请求格式或参数类型不正确"));
    }
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException e) {
        return ResponseEntity.status(e.status()).body(ApiError.of(e.code(), e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(x -> fields.putIfAbsent(x.getField(), x.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "请求参数不合法", MDC.get("traceId"), fields));
    }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthenticated(AuthenticationException e) {
        return ResponseEntity.status(401).body(ApiError.of("UNAUTHENTICATED", "用户名或密码错误，或登录已失效"));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException e) {
        return ResponseEntity.status(403).body(ApiError.of("FORBIDDEN", "没有访问权限"));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(ApiError.of("DATA_CONFLICT", "数据冲突，请刷新后重试"));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(ApiError.of("UPLOAD_TOO_LARGE", "文件超过上传大小限制"));
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException e) {
        return ResponseEntity.status(404).body(ApiError.of("NOT_FOUND", "资源不存在"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        if (e instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(error.getStatusCode()).body(ApiError.of("INVALID_REQUEST", "请求格式、参数或请求方法不正确"));
        }
        // Exception messages and SQL DETAILS may contain user input or credentials.
        log.atError().addKeyValue("event", "http.failed").addKeyValue("exceptionType", e.getClass().getName())
            .addKeyValue("origin", e.getStackTrace().length == 0 ? "unknown" : e.getStackTrace()[0].toString())
            .log("Unhandled request failure");
        return ResponseEntity.internalServerError().body(ApiError.of("INTERNAL_ERROR", "服务暂时无法处理请求，请凭 traceId 联系管理员"));
    }
}
