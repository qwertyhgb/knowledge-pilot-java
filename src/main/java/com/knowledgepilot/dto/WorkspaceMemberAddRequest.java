package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 添加空间成员请求体。
 *
 * <p>注意：workspaceId 不在请求体里，而在 URL 路径中（/workspaces/{workspaceId}/members），
 * 体现"成员是空间的子资源"的 RESTful 语义。</p>
 */
@Data
@Schema(description = "添加空间成员的请求")
public class WorkspaceMemberAddRequest {

    /** 被添加的用户ID，非空 */
    @NotNull(message = "用户ID不能为空")
    @Schema(description = "被添加的用户ID（关联 user_account.id）", example = "2")
    private Long userId;

    /**
     * 空间内角色，可空：不传默认 MEMBER。
     *
     * <p>仅允许 MEMBER：空间所有者具有唯一性，本接口不提供指定 OWNER 的能力
     * （OWNER 由空间创建时确定，或日后经转让流程变更）。</p>
     *
     * <p>正则 (?i) 允许大小写（如 member），Service 层会统一转大写后再校验。</p>
     */
    @Pattern(regexp = "(?i)^MEMBER$", message = "角色只能是 MEMBER")
    @Schema(description = "空间内角色，可空默认 MEMBER；仅允许 MEMBER，不支持指定 OWNER", example = "MEMBER",
            allowableValues = {"MEMBER"}, nullable = true)
    private String role;

    /**
     * 操作者用户ID（谁在执行添加操作，必须是该空间 OWNER）。
     * TODO(接入认证后移除)：当前无登录态，从请求体取值存在冒用风险；
     * JWT/Session 接入后必须改为从登录上下文获取，禁止信任前端传入。
     */
    @NotNull(message = "操作者ID不能为空")
    @Schema(description = "操作者用户ID（临时字段，接入登录后从会话获取）", example = "1")
    private Long operatorId;
}
