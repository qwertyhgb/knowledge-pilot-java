-- KnowledgePilot 数据库初始化（需 root 执行，仅第一次）
-- root 口令由容器环境变量提供，不要写进仓库：
--   docker exec -i knowflow-mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < src/main/resources/db/init-database.sql
--
-- 执行前请把下面 IDENTIFIED BY 的占位值改为你本机要用的口令，并且不要把这个改动提交。

CREATE DATABASE IF NOT EXISTS `knowledge_pilot`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'knowledge_pilot'@'%' IDENTIFIED BY 'replace-with-your-local-password';

GRANT ALL PRIVILEGES ON `knowledge_pilot`.* TO 'knowledge_pilot'@'%';

FLUSH PRIVILEGES;
