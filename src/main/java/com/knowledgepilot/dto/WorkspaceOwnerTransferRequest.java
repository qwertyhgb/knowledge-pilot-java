package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 转让空间所有权请求体。
 *
 * <p>
 * 注意 workspaceId 不在请求体里，而在 URL 路径中（/workspaces/{workspaceId}/owner），
 * 体现"所有者是空间的属性"这一 RESTful 语义。
 * </p>
 *
 * <p>
 * 本接口只接受 userId：角色固定为 OWNER，不允许客户端指定，
 * 与 {@link WorkspaceMemberAddRequest} 只允许 MEMBER 的思路一致——
 * <b>角色由服务端按业务规则决定，不由客户端声明</b>。
 * </p>
 */
@Data
@Schema(description = "转让空间所有权的请求")
public class WorkspaceOwnerTransferRequest {

    /**
     * 新所有者的用户ID，非空。
     *
     * <p>
     * 必须<b>已经是该空间的 MEMBER</b>：本接口不做"顺带把人加进来"，
     * 否则会绕过添加成员接口的整条校验链（账号启用、uk_ws_user 唯一索引兜底）。
     * </p>
     */
    @NotNull(message = "新所有者用户ID不能为空")
    @Schema(description = "新所有者的用户ID（必须已是该空间 MEMBER）", example = "2")
    private Long userId;
}