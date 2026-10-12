package com.knowledgepilot.common;

/**
 * 业务异常，code 同时作为 HTTP 状态码使用（400~599 区间内）。
 *
 * <p>继承 {@link RuntimeException}，属于非受检异常：业务出错是正常分支，
 * 不强迫调用方 try-catch，统一交给 {@code GlobalExceptionHandler} 兜底处理，
 * 转换为统一的 {@code Result} 响应返回给前端。</p>
 *
 * <p>典型用法：</p>
 * <pre>
 * throw BizException.notFound("用户不存在");   // code = 404
 * throw new BizException(500, "解析失败", e); // 带底层原因链
 * </pre>
 */
public class BizException extends RuntimeException {

    /** 序列化版本号：异常类实现了 Serializable，加上可消除 IDE 告警 */
    private static final long serialVersionUID = 1L;

    /** 默认业务错误码：400 Bad Request */
    public static final int DEFAULT_CODE = 400;

    /** 错误码，同时作为 HTTP 状态码返回，声明为 final 保证不可变（线程安全） */
    private final int code;

    /**
     * 使用默认错误码（400）构造异常。
     *
     * @param message 错误描述，会展示给调用方/前端
     */
    public BizException(String message) {
        this(DEFAULT_CODE, message);
    }

    /**
     * 带原因链的构造：包装底层异常时用它，保留原始堆栈方便排查
     */
    public BizException(String message, Throwable cause) {
        this(DEFAULT_CODE, message, cause);
    }

    /**
     * 使用指定错误码构造异常（400~599 区间内）。
     *
     * @param code    错误码，同时作为 HTTP 状态码
     * @param message 错误描述
     */
    public BizException(int code, String message) {
        this(code, message, null);
    }

    /**
     * 全参构造（最底层，所有其他构造器最终都委托到这里）。
     *
     * @param code    错误码，同时作为 HTTP 状态码
     * @param message 错误描述
     * @param cause   底层原因异常，可为 null；传入后异常链得以保留，日志中能看到根因
     */
    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 获取错误码。
     *
     * @return HTTP 状态码（400~599）
     */
    public int getCode() {
        return code;
    }

    // ========== 常用语义的静态工厂 ==========

    /** 400 参数/业务规则错误 */
    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }

    /** 401 未认证 */
    public static BizException unauthorized(String message) {
        return new BizException(401, message);
    }

    /** 403 无权限 */
    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }

    /** 404 资源不存在 */
    public static BizException notFound(String message) {
        return new BizException(404, message);
    }

    /** 409 状态冲突（如邮箱已存在） */
    public static BizException conflict(String message) {
        return new BizException(409, message);
    }
}