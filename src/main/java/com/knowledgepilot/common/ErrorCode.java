package com.knowledgepilot.common;

/**
 * 错误码常量表。
 *
 * <p><b>唯一约定：错误码即 HTTP 状态码。</b>响应体里的 {@code code} 始终等于本次请求的
 * HTTP 状态码，成功固定为 {@link #SUCCESS}（0）。前端按 HTTP 状态码分支即可，
 * 不需要再维护一张"业务码 → 提示语"的映射表。</p>
 *
 * <p><b>为什么不定义 1xxxx / 2xxxx 这类模块级业务码？</b>
 * {@code code} 字段已经承载了 HTTP 语义，再往里塞业务语义会让同一个字段出现两套解释，
 * 立刻产生歧义（例如 10001 该对应 409 还是 200？）。如果将来确实需要更细粒度的
 * 业务码，正确做法是给 {@link Result} 增加独立的 {@code bizCode} 字段，
 * 而不是复用 {@code code}，届时单独设计。</p>
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    /** 成功（HTTP 200） */
    public static final int SUCCESS = 0;

    /** 400 请求参数不合法或违反业务规则 */
    public static final int BAD_REQUEST = 400;

    /** 401 未认证 */
    public static final int UNAUTHORIZED = 401;

    /** 403 已认证但无权限 */
    public static final int FORBIDDEN = 403;

    /** 404 资源不存在 */
    public static final int NOT_FOUND = 404;

    /** 409 与现有资源状态冲突（如用户名、邮箱已存在） */
    public static final int CONFLICT = 409;

    /** 500 服务器内部错误 */
    public static final int SERVER_ERROR = 500;
}
