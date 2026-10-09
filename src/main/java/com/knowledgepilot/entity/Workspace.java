package com.knowledgepilot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 工作空间（workspace）。
 *
 * <p>注意：这里<b>没有 owner_id</b>，当前所有权的唯一事实来源是 workspace_member.role='OWNER'；
 * creator_id 只做审计（谁点的创建按钮），创建后永不变。
 *
 * <p>删除策略：逻辑删除（{@code deleted} 列 + {@link TableLogic}），空间下挂文档/成员，需可恢复。
 */
@Data
@TableName("workspace")
public class Workspace {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 空间名称，允许中文、允许重复 */
    private String name;

    /** 一句话简介，可空 */
    private String description;

    /** 创建者，关联 user_account.id；不可变，仅审计。创建者 ≠ 当前所有者（转让后仍不变） */
    private Long creatorId;

    /** 状态：0-归档停用，1-正常（V0.1 先用 Integer，暂不上枚举） */
    private Integer status;

    /** 逻辑删除标记：0-正常，1-已删除，与全项目约定一致 */
    @TableLogic
    private Integer deleted;

    /** 创建时间，AuditFieldHandler 自动填充，库表默认值兜底 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后修改时间，AuditFieldHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
