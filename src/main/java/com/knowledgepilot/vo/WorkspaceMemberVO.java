package com.knowledgepilot.vo;

import java.time.LocalDateTime;

import com.knowledgepilot.entity.WorkspaceMember;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 空间成员出参（View Object）。
 *
 * <p>
 * 只承载可对前端暴露的字段，实体转 VO 的字段映射规则收口在 {@link #from} 里。
 * 成员表本身无敏感字段（物理删除、无 deleted 列），此处主要是展示口径的统一。
 * </p>
 */
@Data
@Schema(description = "空间成员信息")
public class WorkspaceMemberVO {

    @Schema(description = "成员记录ID", example = "1")
    private Long id;

    @Schema(description = "工作空间ID", example = "1")
    private Long workspaceId;

    @Schema(description = "用户ID（关联 user_account.id）", example = "2")
    private Long userId;

    @Schema(description = "空间内角色：OWNER-所有者，MEMBER-普通成员", example = "MEMBER")
    private String role;

    @Schema(description = "加入时间")
    private LocalDateTime createTime;

    @Schema(description = "最后修改时间")
    private LocalDateTime updateTime;

    /**
     * 实体 → VO 的转换工厂。
     *
     * <p>
     * 收口字段映射规则，外部只调用 {@code WorkspaceMemberVO.from(member)}，
     * 避免散落的转换代码漏改。
     * </p>
     *
     * @param member 成员实体，可为 null
     * @return 转换后的展示对象；入参为 null 时返回 null
     */
    public static WorkspaceMemberVO from(WorkspaceMember member) {
        if (member == null) {
            return null;
        }
        WorkspaceMemberVO vo = new WorkspaceMemberVO();
        vo.setId(member.getId());
        vo.setWorkspaceId(member.getWorkspaceId());
        vo.setUserId(member.getUserId());
        vo.setRole(member.getRole());
        vo.setCreateTime(member.getCreateTime());
        vo.setUpdateTime(member.getUpdateTime());
        return vo;
    }
}
