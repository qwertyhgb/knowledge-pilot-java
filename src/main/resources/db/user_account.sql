-- =============================================================================
-- KnowledgePilot 用户账号表（user_account）
-- =============================================================================
-- 【怎么执行这个脚本】
-- 用业务账号 knowledge_pilot 连接，命令里不写死口令、口令走交互式输入，
-- 这样密码不会残留在命令行历史或仓库里：
--   docker exec -it knowflow-mysql mysql -uknowledge_pilot -p knowledge_pilot < src/main/resources/db/user_account.sql
--
-- 【新手须知】
-- - 本文件是 DDL（建表语句），描述的是“表长什么样”，不是插入数据。
-- - 表结构由这里手工执行，应用启动不会自动建表（配置里 spring.sql.init.mode=never）。
-- - 每张表都写成 “IF NOT EXISTS”，重复执行不会报错，方便反复导入。
-- - 每个反引号 `name` 里的都是“标识符”（表名/列名）。反引号能避免和 MySQL 关键字冲突，
--   例如 status 这类词，用反引号包起来最保险。
-- =============================================================================

-- 告诉数据库：本脚本里的文字按 utf8mb4 编码解析。
-- utf8mb4 是 MySQL 里能存 emoji 和大部分生僻字的字符集，本项目全库统一用它。
SET NAMES utf8mb4;

-- 创建表。IF NOT EXISTS = 如果同名表已存在就跳过，不报错。
CREATE TABLE IF NOT EXISTS `user_account`
(
    -- ---------- 主键 ----------
    -- id：每一行数据的唯一编号，由数据库自动生成，永远不用手动填。
    -- BIGINT UNSIGNED：大整数且“无符号”，即只能是正数，范围约 0 ~ 1844 亿亿，够存海量用户。
    -- AUTO_INCREMENT：自增，插入第一条是 1，之后依次 2、3……
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',

    -- ---------- 登录凭据 ----------
    -- username：用户登录时输入的名字，要求全局唯一（见下面 uk_username 唯一索引）。
    -- VARCHAR(64)：可变长字符串，最多 64 个字符；按实际用多少存多少，比定长省空间。
    -- NOT NULL：这一列不允许为空，登录名必须有。
    `username`        VARCHAR(64)     NOT NULL COMMENT '登录名，全局唯一',

    -- password_hash：存放加密后的口令，绝不存明文密码！
    -- 我们用的是 BCrypt 算法的散列值（约 60 个字符），这里给到 100 是为将来换更长算法留余量。
    -- 校验登录时：把用户输入的密码再散列一次，跟这里存的值比对，而不是解密（散列不可逆）。
    `password_hash`   VARCHAR(100)    NOT NULL COMMENT '口令散列值（BCrypt），禁止存明文',

    -- ---------- 联系方式（允许为空） ----------
    -- email：可为空（不填邮箱的用户也能注册）。
    -- 之所以敢设唯一索引，是因为 MySQL 的唯一索引不约束 NULL——多个空邮箱不算重复，互不冲突。
    `email`           VARCHAR(128) NULL COMMENT '邮箱，用于登录/找回，唯一（NULL 不参与约束）',

    -- ---------- 状态 ----------
    -- status：账号是否可用。0=禁用（封禁，记录还在但不许登录），1=启用。
    -- DEFAULT 1：新注册的账号默认正常可用。
    -- 注意它和下面的 deleted 是两回事：status 是“业务上停用”，deleted 是“记录被删除”。
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',

    -- ---------- 逻辑删除（软删除）标记 ----------
    -- deleted：0=正常，1=已删除。
    -- “逻辑删除”指：删数据时不真的从库里抹掉，只把这一列置 1，查询时自动过滤掉。
    -- 好处是可恢复、留痕；这个约定全项目通用，MyBatis-Plus 全局配置会自动处理它。
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常，1-已删除',

    -- ---------- 审计时间戳 ----------
    -- create_time：记录创建时间。DEFAULT CURRENT_TIMESTAMP = 插入时自动填当前时间。
    -- update_time：记录最后修改时间。ON UPDATE CURRENT_TIMESTAMP = 该行被更新时自动刷新。
    -- 这两列应用层也由 AuditFieldHandler 自动填充，这里的默认值是兜底，双保险。
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- ---------- 索引与约束 ----------
    -- PRIMARY KEY：主键，唯一且非空，一个表只能有一个；数据按它物理排序，是查询最快的路径。
    PRIMARY KEY (`id`),

    -- UNIQUE KEY：唯一索引，保证这列的值全表不重复，重复插入会直接报错。
    -- 同时能加速“按用户名查用户”这类查询。
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)

    -- 索引命名习惯：主键用 pk_、唯一索引用 uk_（unique key）。
    -- status 只有 0/1、区分度极低，V0.1 先不给它建单列索引；将来用 EXPLAIN 证实需要再补，别凭感觉建。
) ENGINE = InnoDB                  -- 存储引擎：InnoDB 支持事务、外键、行级锁，是 MySQL 8 的默认与推荐选择。
  DEFAULT CHARSET = utf8mb4        -- 表的默认字符集，能存中文和 emoji。
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户账号';
  -- COLLATE 排序规则：utf8mb4_0900_ai_ci 是 MySQL 8.0+ 的默认排序规则。
  --   末尾的 ci = case-insensitive（大小写不敏感），即 'Tom' 和 'tom' 视为相同。
  -- COMMENT 是给这张表本身写的备注说明，会存进数据库元数据，可用 SHOW TABLE STATUS 查看。
