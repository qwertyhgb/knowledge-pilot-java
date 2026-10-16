-- =============================================================================
-- KnowledgePilot MySQL 索引实验脚本（学习专用，非业务代码）
-- =============================================================================
-- 【这是什么】
-- 一张纯学习用的实验表 idx_lab_member，专门用于验证 MySQL 索引的行为：
--   - 联合索引最左匹配原则（核心）
--   - 回表 vs 覆盖索引
--   - 索引失效的典型场景
--   - EXPLAIN 与 EXPLAIN ANALYZE 的区别
--
-- 【为什么单独建表，不在业务表上做】
-- 1. 零污染：实验要灌 10 万行，不影响 workspace_member 等业务表；
-- 2. 排除干扰：业务表有逻辑删除、JOIN 等因素，会干扰 EXPLAIN 的观察；
-- 3. 结论可迁移：索引结构照抄 workspace_member，实验结论可直接用于业务设计。
--
-- 【本文件在哪】
--   docs/notes/mysql/index-lab.sql —— 与笔记 01-index.md 放在一起的学习资产。
--   刻意不放在 src/main/resources/db/：那里的文件会被 Maven 打进最终 jar。
--
-- 【怎么执行】
--   在 Navicat 中打开 knowledge_pilot 库，新建查询，粘贴本文件内容后运行。
--   注意：必须分批执行（建表 / 造数 / 各实验分别跑），造数据那段可能需要几十秒。
--   ⚠️ 不要无脑整段执行到文件末尾：最后的清理语句一旦取消注释会直接 DROP 实验表。
--
-- 【安全性】
--   本脚本只操作 idx_lab_member 一张表，不触碰任何业务表。
--   清理方式：手动执行 DROP TABLE IF EXISTS idx_lab_member;
-- =============================================================================

SET NAMES utf8mb4;

-- 实验表：验证索引行为。字段刻意精简为5 个，去掉业务表的一切干扰因素。
CREATE TABLE IF NOT EXISTS `idx_lab_member`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `workspace_id` BIGINT UNSIGNED NOT NULL COMMENT '空间ID',
    `role`         VARCHAR(32)     NOT NULL DEFAULT 'MEMBER' COMMENT '角色',
    `create_time`  DATETIME        NOT NULL COMMENT '加入时间',

    PRIMARY KEY (`id`),

    -- 索引1：单列索引，作为对照组
    KEY `idx_user` (`user_id`),

    -- 索引2：联合索引，user_id 在前
    -- 与索引3 `idx_ws_user` 字段相同、顺序相反，两者构成验证「最左匹配」的核心对照组
    KEY `idx_user_workspace` (`user_id`, `workspace_id`),

    -- 索引3：联合索引，workspace_id 在前
    -- 索引2 的"顺序相反"版本：字段完全相同，只有列顺序不同，
    -- 因此哪些查询能命中、哪些不能，只看列顺序——这就是最左匹配的直接证据
    KEY `idx_ws_user` (`workspace_id`, `user_id`),

    -- 索引4：create_time 在前，为实验 5（ORDER BY 与 filesort）准备，
    -- 不与索引2 构成对照（字段不同）。注意别把它当成"索引2 的顺序相反版"
    KEY `idx_create_user` (`create_time`, `user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '索引实验表（学习专用）';

-- ✅ 完整正确的递归 CTE 造数脚本
-- 说明：先用一个递归 CTE 生成 1~100000 的序列，
--       再用 CASE 把 user_id 映射成"倾斜分布"。
SET cte_max_recursion_depth = 200000;

INSERT INTO `idx_lab_member` (`user_id`, `workspace_id`, `role`, `create_time`)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT
    -- 数据倾斜设计：
    -- n <= 5000    时，user_id 固定为 1（重度用户，5000 条记录）
    -- n > 5000     时，user_id 为 n - 5000（普通用户，各 1 条）
    CASE WHEN n <= 5000 THEN 1 ELSE n - 5000 END        AS user_id,
    (n % 1000) + 1                                      AS workspace_id,
    IF(n % 10 = 0, 'OWNER', 'MEMBER')                  AS role,
    DATE_ADD('2024-01-01 00:00:00', INTERVAL n MINUTE) AS create_time
FROM seq;

-- 验证：应该正好100000 行
SELECT COUNT(*) AS total FROM idx_lab_member;

-- 验证数据倾斜：user_id=1 应该正好有 5000 条
SELECT user_id, COUNT(*) AS cnt
FROM idx_lab_member
GROUP BY user_id
ORDER BY cnt DESC
LIMIT 5;

-- 实验结束后想清空重来，任选一条【手动取消注释】后再执行：
--   ⚠️ 这两句默认注释掉，是为了防止"整段执行"时把刚造好的 10 万行数据立刻删掉。
-- DROP TABLE IF EXISTS idx_lab_member;  -- 删表重来（推荐）
-- TRUNCATE TABLE idx_lab_member;        -- 只清数据保留表结构
