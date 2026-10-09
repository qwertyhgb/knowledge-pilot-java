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
 * 用户账号（user_account）。
 *
 * <p>删除策略：逻辑删除（{@code deleted} 列 + {@link TableLogic}），账号是核心资产，删除需留痕、可恢复。
 */
@Data
@TableName("user_account")
public class UserAccount {

    /**
     * 主键，数据库自增。
     * 全局已配置 id-type: auto，这里显式写出是把意图固化在代码里（自文档化）。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录名，全局唯一（唯一性由库表 uk_username 保证） */
    private String username;

    /** 口令散列值（BCrypt），禁止存明文；驼峰 passwordHash 由全局 map-underscore-to-camel-case 映射到 password_hash */
    private String passwordHash;

    /** 邮箱，可空；多个空邮箱不算重复（MySQL 唯一索引不约束 NULL） */
    private String email;

    /** 状态：0-禁用，1-启用（V0.1 先用 Integer，暂不上枚举） */
    private Integer status;

    /**
     * 逻辑删除标记：0-正常，1-已删除。
     * 标了 {@link TableLogic} 后，MP 自动生成的查询会追加 deleted=0、deleteById 变成 UPDATE deleted=1。
     * 字段名与全局配置 logic-delete-field: deleted 一致。
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间。业务层由 AuditFieldHandler 自动填充（strictInsertFill，仅在值为 null 时写入），
     * 库表 DEFAULT CURRENT_TIMESTAMP 兜底。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后修改时间。insert 时填充一次，update 时刷新（AuditFieldHandler.updateFill） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
