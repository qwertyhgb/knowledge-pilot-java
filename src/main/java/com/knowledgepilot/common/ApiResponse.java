package com.knowledgepilot.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "统一响应结构")
public record ApiResponse<T>(
        @Schema(description = "业务码，0 表示成功") int code,
        @Schema(description = "提示信息") String message,
        @Schema(description = "响应数据") T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
