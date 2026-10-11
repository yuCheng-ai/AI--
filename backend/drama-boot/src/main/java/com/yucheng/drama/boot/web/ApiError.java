package com.yucheng.drama.boot.web;

import java.util.Map;
import org.slf4j.MDC;

public record ApiError(String code, String message, String traceId, Map<String, String> fieldErrors) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, MDC.get("traceId"), Map.of());
    }
}
