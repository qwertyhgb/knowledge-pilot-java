package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建用户（注册）请求体。
 *
 * <p>校验链路：Controller 参数上标 {@code @Valid} → Spring 触发 JSR-380 校验
 * → 失败抛 ConstraintViolationException / MethodArgumentNotValidException
 * → 由 GlobalExceptionHandler 统一转成 400 + "字段: 提示" 的错误信息。
 *
 * <p>注意：本类必须有 getter/setter（用 Lombok {@code @Data} 生成），
 * 否则 Jackson 反序列化时字段永远是 null，校验形同虚设。
 */
@Data
@Schema(description = "创建用户请求")
public class UserCreateRequest {

    /** 登录名，3~64 位；唯一性不在这里校验，由库表 uk_username 兜底（重复会在 SQL 层报错） */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度必须在 3~64 之间")
    @Schema(description = "登录名，3~64 位", example = "zhangsan")
    private String username;

    /**
     * 明文密码，6~64 位。
     * 这里收到的是明文，Service 层用 BCrypt 散列后才允许写入 password_hash，
     * 任何地方都不允许把明文或散列值回显到响应里。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在 6~64 之间")
    @Schema(description = "明文密码，6~64 位（仅传输用，落库前必须 BCrypt 散列）", example = "abc123")
    private String password;

    /**
     * 邮箱，可空（不加 @NotBlank = 允许不填，对应库表 email 允许 NULL）。
     * 格式由 @Email 保证；为空串/null 都能通过校验，这是 Bean Validation 的既定行为。
     * 唯一性由库表 uk_email 兜底（多个空邮箱不冲突）。
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    @Schema(description = "邮箱，可空", example = "zhangsan@example.com", nullable = true)
    private String email;
}
