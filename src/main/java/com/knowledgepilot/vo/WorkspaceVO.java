package com.knowledgepilot.vo;

import com.knowledgepilot.entity.Workspace;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作空间信息出参（View Object）。
 *
 * <p>
 * 采用普通 POJO 类结合 Lombok 声明，与项目整体风格保持一致。
 * 只承载可对前端暴露的展示字段：
 * {@code deleted}（逻辑删除标记）属于内部审计字段，禁止在此暴露。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工作空间信息")
public class WorkspaceVO {

    @Schema(description = "工作空间ID", example = "1")
    private Long id;

    @Schema(description = "空间名称", example = "研发中心知识库")
    private String name;

    @Schema(description = "空间简介，可空", example = "存放核心技术架构文档与研发规范", nullable = true)
    private String description;

    @Schema(description = "创建者用户ID（仅作审计留痕）", example = "1")
    private Long creatorId;

    @Schema(description = "状态：0-归档停用，1-正常", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "最后修改时间")
    private LocalDateTime updateTime;

    /**
     * 实体 → VO 的转换工厂。
     *
     * <p>
     * 收口字段映射规则，外部调用只需 {@code WorkspaceVO.from(workspace)}，
     * 避免字段泄漏或散落的转换代码。
     * </p>
     *
     * @param workspace 空间实体，调用方需保证非 null
     * @return 转换后的展示对象
     */
    public static WorkspaceVO from(Workspace workspace) {
        if (workspace == null) {
            return null;
        }
        WorkspaceVO vo = new WorkspaceVO();
        vo.setId(workspace.getId());
        vo.setName(workspace.getName());
        vo.setDescription(workspace.getDescription());
        vo.setCreatorId(workspace.getCreatorId());
        vo.setStatus(workspace.getStatus());
        vo.setCreateTime(workspace.getCreateTime());
        vo.setUpdateTime(workspace.getUpdateTime());
        return vo;
    }
}