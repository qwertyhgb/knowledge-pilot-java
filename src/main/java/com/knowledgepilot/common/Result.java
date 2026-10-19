package com.knowledgepilot.common;

import lombok.Data;

/**
 * 统一响应体。
 *
 * <p>
 * 所有接口固定返回 {@code {code, message, data}} 三段结构：
 * </p>
 * <ul>
 *   <li>{@code code}：与本次响应的 HTTP 状态码保持一致，成功固定为
 *       {@link ErrorCode#SUCCESS}（0）；不另设第二套业务码；</li>
 *   <li>{@code message}：成功为 {@code "ok"}，失败为可直接展示给用户的错误描述；</li>
 *   <li>{@code data}：业务数据；失败时为 null，全局 non_null 配置使其不参与序列化。</li>
 * </ul>
 *
 * <p>
 * 为什么用静态工厂而不是公开构造器：避免调用方拼出 code/message 不自洽的响应，
 * 成功与失败只能从 {@link #success} / {@link #error} 两个入口产生。
 * </p>
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {

    /** 响应码：与本次响应的 HTTP 状态码一致，成功固定为 0（见 {@link ErrorCode#SUCCESS}） */
    private int code;

    /** 提示信息：成功为 "ok"，失败为可直接展示给用户的错误描述 */
    private String message;

    /** 业务数据：失败时为 null；全局 non_null 配置使其不参与序列化 */
    private T data;

    /** Jackson 反序列化/框架实例化需要，业务代码应使用静态工厂构造 */
    public Result() {
    }

    /**
     * 成功响应（带业务数据）。
     *
     * @param data 业务数据，允许为 null（此时 JSON 中不出现 data 字段）
     * @param <T>  业务数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.SUCCESS);
        result.setMessage("ok");
        result.setData(data);
        return result;
    }

    /**
     * 成功响应（无业务数据），用于登出、删除等只需确认结果的接口。
     *
     * @param <T> 业务数据类型，调用方通常按 Void 推断
     * @return 成功响应，data 为 null
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 成功响应（自定义提示语）。
     *
     * @param data    业务数据，允许为 null
     * @param message 自定义成功提示
     * @param <T>     业务数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data, String message) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.SUCCESS);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    /**
     * 失败响应。
     *
     * @param code    错误码，与 HTTP 状态码保持一致（400~599）
     * @param message 错误描述，会展示给调用方
     * @param <T>     业务数据类型
     * @return 失败响应，data 为 null
     */
    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(null);
        return result;
    }

    /**
     * 失败响应（通用提示语 "error"）。
     *
     * @param code 错误码，与 HTTP 状态码保持一致（400~599）
     * @param <T>  业务数据类型
     * @return 失败响应，data 为 null
     */
    public static <T> Result<T> error(int code) {
        return error(code, "error");
    }
}
