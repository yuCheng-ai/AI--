package com.yucheng.drama.kernel.api;

/** An expected application failure with a safe, client-visible message. */
public final class BusinessException extends RuntimeException {
    private final int status;
    private final String code;

    public BusinessException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public static BusinessException badRequest(String code, String message) { return new BusinessException(400, code, message); }
    public static BusinessException forbidden(String code, String message) { return new BusinessException(403, code, message); }
    public static BusinessException notFound(String code, String message) { return new BusinessException(404, code, message); }
    public static BusinessException conflict(String code, String message) { return new BusinessException(409, code, message); }
}
