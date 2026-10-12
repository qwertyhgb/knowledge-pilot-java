package com.knowledgepilot.common;

/**
 * 错误码常量表
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    // ===== 通用 =====
    public static final int SUCCESS = 0;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int SERVER_ERROR = 500;

    // ===== 用户模块 1xxxx =====
    public static final int USER_EMAIL_EXISTS = 10001;
    public static final int USER_USERNAME_EXISTS = 10002;
    public static final int USER_NOT_FOUND = 10003;

    // ===== 工作空间模块 2xxxx =====
    public static final int WORKSPACE_NOT_FOUND = 20001;
}