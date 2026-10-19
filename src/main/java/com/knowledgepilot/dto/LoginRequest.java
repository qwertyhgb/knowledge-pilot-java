package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求体。
 *
 * <p>校验链路：Controller 参数上标 {@code @Valid} → Spring 触发 JSR-380 校验
 * → 失败由 {@code GlobalExceptionHandler} 统一转成 400 + "字段: 提示" 的错误信息。</p>
 *
 * <p><b>为什么约束比 {@link UserCreateRequest} 松（有意为之，不是漏写）：</b></p>
 * <ul>
 *   <li>不设 {@code min}：口令短/用户名短让它自然匹配失败即可。登录时提前拒返
 *       等于告诉攻击者"合法口令至少 6 位"，是在泄露策略；</li>
 *   <li>不加 {@code @Pattern}：本 DTO 的约束必须是注册约束的<b>子集</b>，
 *       否则会出现"能注册却登不上"的账号；</li>
 *   <li>{@code max = 64} 与 {@code user_account.username VARCHAR(64)} 对齐，
 *       同时防止超长口令白耗 BCrypt 算力（BCrypt 故意慢，是大 Body 的 DoS 入口）。</li>
 * </ul>
 *
 * <p><b>给 Service 层的两条硬约束（由注册链路的契约决定，抄错任意一条都会出 Bug）：</b></p>
 * <ol>
 *   <li>username 查库前必须 {@code trim()}：注册时用户名是 trim 后落库的
 *       （见 {@code UserAccountServiceImpl#createUser}），不 trim 会让复制粘贴
 *       带入尾随空格的用户永远登录失败；</li>
 *   <li>password 必须<b>原样</b>参与 BCrypt 比对，禁止 trim：注册时口令原样散列，
 *       空格是口令的一部分，trim 反而会把含空格的密码锁死。</li>
 * </ol>
 *
 * <p>长度只按字符数约束（{@code @Size(max = 64)}），Service 不重复校验字节数。
 * 已知边界：多字节字符（如 64 个汉字）可能超过 BCrypt 的 72 字节处理上限，
 * 当前不特殊处理，等真实问题出现再统一注册与登录的口径。</p>
 *
 * <p>注意：本类必须有 getter/setter（用 Lombok {@code @Data} 生成），
 * 否则 Jackson 反序列化时字段永远是 null，校验形同虚设。</p>
 */
@Data
@Schema(description = "登录请求")
public class LoginRequest {

    /**
     * 登录名，最长 64 位。
     * 非空由 {@code @NotBlank} 保证（纯空白串也算空）；格式与唯一性在注册时
     * 已由 uk_username 和注册校验兜底，登录只做限长，trim 与匹配交给 Service。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64")
    @Schema(description = "登录名", example = "zhangsan")
    private String username;

    /**
     * 明文密码，最长 64 位，仅传输与比对用。
     * 与库中 BCrypt 散列值比对（散列不可逆，不存在"解密"）；
     * 禁止 trim、禁止落库、禁止回显到响应或日志。
     */
    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不能超过 64")
    @Schema(description = "明文密码（仅传输用，服务端与 BCrypt 散列值比对）", example = "abc123")
    private String password;
}
