-- =============================================================================
-- KnowledgePilot 工作空间成员表（workspace_member）
-- =============================================================================
-- 【怎么执行】口令交互式输入，不落进命令行或仓库：
--   docker exec -it knowflow-mysql mysql -uknowledge_pilot -p knowledge_pilot < src/main/resources/db/workspace_member.sql
--
-- 【这是什么】
-- 承载 workspace 与 user_account 的「多对多」关系的中间表：
--   workspace  ┐
--              ├─ workspace_member（每行 = “某用户在某空间里”，带上他在该空间的角色）
-- user_account ┘
-- 每一行代表一个事实：“user_id 这个用户，是 workspace_id 这个空间的成员，角色是 role”。
-- 想表达“A 用户加入了 B、C 两个空间”，就是两行记录。
-- 【为什么这张表不用逻辑删除】成员关系是纯关联，不需要留痕：退出就物理 DELETE 该行。
--   若这里也用 deleted=0/1 软删除，会和下面的 uk_ws_user 唯一索引打架——用户“退出(置1)”后
--   “重新加入”时，唯一索引仍能看到那条旧记录，INSERT 会直接失败（逻辑删除最典型的副作用）。
--   物理删除让“重新加入”就是干净地插一行新记录，天然没有这个问题。
-- 【执行顺序】本表逻辑引用 workspace.id 与 user_account.id（未建库级外键，仅约定），
--   建议先执行 user_account.sql、workspace.sql，再执行本文件。
-- =============================================================================

-- 告诉数据库：本脚本里的文字按 utf8mb4 编码解析（全项目统一用它，能存中文和 emoji）。
SET NAMES utf8mb4;

-- 创建表。IF NOT EXISTS = 如果同名表已存在就跳过，不报错。
CREATE TABLE IF NOT EXISTS `workspace_member`
(
    -- 主键：数据库自动生成的自增编号，对内关联用。
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',

    -- workspace_id / user_id：两个外键列（这里是逻辑关联，不建库级 FOREIGN KEY，和现有表风格一致），
    -- 分别指向 workspace.id 和 user_account.id；类型都用 BIGINT UNSIGNED 保持一致。
    `workspace_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 workspace.id',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '关联 user_account.id',

    -- role：该用户“在这个空间里”的角色，只分两种：OWNER-所有者 / MEMBER-普通成员。
    --   OWNER 有该空间的管理权（转让、增删成员等），MEMBER 是普通成员；
    --   同一用户在不同空间可以有不同角色（在 X 是 OWNER、在 Y 只是 MEMBER）。
    `role`         VARCHAR(32)     NOT NULL DEFAULT 'MEMBER' COMMENT '空间内角色：OWNER-所有者，MEMBER-普通成员（默认普通成员）',

    -- 加入时间 / 更新时间：由应用层 AuditFieldHandler 自动填充，这里的默认值是兜底。
    -- （本表无 deleted 列：成员退出走物理删除，原因见文件头说明）
    `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    -- 联合唯一索引：同一个用户在同一个空间里只能有一条成员记录。
    -- 这正是“多对多去重”的核心，也是防止重复添加同一成员的关键约束。
    -- 配合“退出=物理删除”，重新加入就是干净地 INSERT 新行，不会被已退出的旧行卡住。
    UNIQUE KEY `uk_ws_user` (`workspace_id`, `user_id`),
    -- 按 user_id 建索引：支撑“查我加入的所有空间”。
    -- 说明：“查某空间的所有成员”能直接复用上面 uk_ws_user 的最左前缀 workspace_id，无需再单建索引。
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='工作空间成员（workspace 与 user 的多对多关联表）';
