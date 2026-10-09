-- =============================================================================
-- KnowledgePilot 工作空间表（workspace）
-- =============================================================================
-- 【怎么执行】口令交互式输入，不落进命令行或仓库：
--   docker exec -it knowflow-mysql mysql -uknowledge_pilot -p knowledge_pilot < src/main/resources/db/workspace.sql
--
-- 【这是什么】
-- workspace 是文档的容器与权限边界。它和 user_account 是「多对多」关系：
-- 一个用户可加入多个空间，一个空间里有多个用户。多对多由另一张中间表
-- workspace_member 承载，定义在同目录的 workspace_member.sql 里。
-- =============================================================================

-- 告诉数据库：本脚本里的文字按 utf8mb4 编码解析（全项目统一用它，能存中文和 emoji）。
SET NAMES utf8mb4;

-- 创建表。IF NOT EXISTS = 如果同名表已存在就跳过，不报错。
CREATE TABLE IF NOT EXISTS `workspace`
(
    -- 主键：数据库自动生成的自增编号，对内关联用。
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',

    -- name：给人看的空间名称，允许中文、允许重复。
    `name`        VARCHAR(128)    NOT NULL COMMENT '空间名称',

    -- description：一句话简介，可空。
    `description` VARCHAR(512) NULL COMMENT '空间简介',

    -- creator_id：空间的创建者，关联 user_account.id，一旦写入就不再变（纯审计用途）。
    -- 类型必须是 BIGINT UNSIGNED，和 user_account.id 完全一致，否则将来 JOIN 会因符号位不匹配踩坑。
    -- 【为什么不叫 owner】创建者 ≠ 当前所有者。空间转让后，“谁建的”仍是张三，
    --   但“谁是 OWNER”要看 workspace_member 里 role='OWNER' 那一行。这里只留 creator_id，
    --   避免同一件事两处存、互相矛盾（当前所有权单一数据源在成员表）。
    `creator_id`  BIGINT UNSIGNED NOT NULL COMMENT '创建者，关联 user_account.id（不可变，仅审计）',

    -- status：空间状态（“是否停用”）。0-归档停用，1-正常。
    `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-归档停用，1-正常',

    -- 逻辑删除：与全项目约定一致，删除只置 1、不物理抹掉，MyBatis-Plus 全局配置会自动过滤。
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常，1-已删除',

    -- 审计时间戳：由应用层 AuditFieldHandler 自动填充，这里的默认值是兜底。
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    -- 按 creator_id 建普通索引：支撑“我创建的所有空间”这类查询。
    KEY `idx_creator` (`creator_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='工作空间';
