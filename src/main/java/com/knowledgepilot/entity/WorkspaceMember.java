package com.knowledgepilot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 工作空间成员（workspace_member），承载 workspace 与 user_account 的多对多关系。
 *
 * <p><b>为什么没有 {@code deleted} 字段，也不标 {@link TableLogic}？</b>
 * <ul>
 *   <li>成员关系是纯关联，退出不需要留痕，约定为物理 DELETE；</li>
 *   <li>若软删除（置 1），{@code uk_ws_user(workspace_id, user_id)} 唯一索引仍能看到旧行，
 *       用户“重新加入”时 INSERT 会直接冲突失败——软删除与唯一索引天然打架；</li>
 *   <li>全局 logic-delete-field 指定的是属性名 deleted，本实体没有该属性，
 *       MP 查不到逻辑删除列即按物理删除处理，查询也不会追加 deleted=0 条件。</li>
 * </ul>
 */
@Data
@TableName("workspace_member")
public class WorkspaceMember {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联 workspace.id */
    private Long workspaceId;

    /** 关联 user_account.id */
    private Long userId;

    /** 空间内角色：OWNER-所有者，MEMBER-普通成员（V0.1 先用 String，暂不上枚举） */
    private String role;

    /** 加入时间，AuditFieldHandler 自动填充，库表默认值兜底 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后修改时间（如转让角色时刷新），AuditFieldHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
