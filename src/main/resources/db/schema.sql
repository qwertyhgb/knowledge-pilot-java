-- KnowledgePilot 表结构（业务账号执行，口令交互式输入，避免写进命令行或仓库）
-- docker exec -it knowflow-mysql mysql -uknowledge_pilot -p knowledge_pilot < src/main/resources/db/schema.sql

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `knowledge_doc`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`       VARCHAR(200)    NOT NULL COMMENT '标题',
    `content`     MEDIUMTEXT NULL COMMENT '正文内容',
    `category`    VARCHAR(64)     NOT NULL DEFAULT 'general' COMMENT '分类',
    `tags`        VARCHAR(255) NULL COMMENT '标签，英文逗号分隔',
    `status`      TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-草稿，1-已发布',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常，1-已删除',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_status` (`category`, `status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='知识文档';
