package com.knowledgepilot.common;

/**
 * 业务异常，code 同时作为 HTTP 状态码使用（400~599 区间内）。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
