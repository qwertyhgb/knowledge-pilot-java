# KnowledgePilot

Spring Boot 4 + MyBatis-Plus + MySQL 的知识库服务，开箱即用地跑通 Web / ORM / 参数校验 / 统一响应与异常 / 逻辑删除 / 审计字段自动填充 / Swagger UI / 健康检查。

> **当前进度（V0.1）**：基础设施与分层骨架已成型，业务只落地了 **用户注册** 与 **创建工作空间** 两条写入链路。
> 登录鉴权、工作空间查询/成员管理、文档域（知识库真正的核心）都还没开始，接口清单里没有的就是没有。

## 技术栈

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Spring Boot | 4.1.1 | `spring-boot-starter-parent`；Boot 4 里 `spring-boot-starter-web` 已更名为 `spring-boot-starter-webmvc` |
| Java | 21 | `java.version=21`，本机 `D:\software\jdk-21.0.8` |
| Maven | 3.9.12 | 本机 `D:\software\apache-maven-3.9.12` |
| MyBatis-Plus | 3.5.17 | `mybatis-plus-spring-boot4-starter`（Boot 4 专用）+ `mybatis-plus-jsqlparser`（3.5.9 起分页等拦截器需显式引入） |
| MySQL | 8.4 | Docker 容器 `knowflow-mysql`，映射 `3306:3306`，驱动 `mysql-connector-j`（Boot 托管） |
| springdoc-openapi | 3.1.0 | `springdoc-openapi-starter-webmvc-ui` |
| spring-security-crypto | Boot 托管 | **只用于 `BCryptPasswordEncoder`**，未启用 Security 过滤器链，因此现在没有任何认证/鉴权 |
| Lombok | Boot 托管 | 编译期注解处理器，打包时从 jar 中排除 |

MyBatis-Plus 3.5.17 已按模块拆分，代码里的 `IService` / `ServiceImpl` 来自新包路径 `com.baomidou.mybatisplus.spring.service.*`，不是旧版本教程里的 `extension.service.*`。

## 前置条件

数据库使用本机 Docker 里已有的 MySQL 实例，**只需初始化一次**。仓库里不保存任何口令：`init-database.sql` 里是占位值，真实口令只存在于本机未提交的 `application-dev-local.yml` 或环境变量中。

```bash
# 1. 启动容器（该容器 restart policy 为 no，机器重启后需要重新启动）
docker start knowflow-mysql

# 2. 建库 + 建业务账号：root 口令从容器环境变量取，不落盘
#    执行前先编辑 src/main/resources/db/init-database.sql，把 IDENTIFIED BY 的占位值改成本机口令，且不要提交该改动
docker exec -i knowflow-mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' \
  < src/main/resources/db/init-database.sql

# 3. 建表：按 user_account → workspace → workspace_member 的顺序执行（后者逻辑引用前者的主键）
#    口令交互式输入，不落进命令行历史；脚本都写了 IF NOT EXISTS，可重复执行
docker exec -it knowflow-mysql mysql -uknowledge_pilot -p knowledge_pilot \
  < src/main/resources/db/user_account.sql
docker exec -it knowflow-mysql mysql -uknowledge_pilot -p knowledge_pilot \
  < src/main/resources/db/workspace.sql
docker exec -it knowflow-mysql mysql -uknowledge_pilot -p knowledge_pilot \
  < src/main/resources/db/workspace_member.sql

# 4. 让应用拿到口令：复制模板后填入第 2 步设置的口令
cp application-dev-local.yml.example application-dev-local.yml
```

`application-dev-local.yml` 已被 `.gitignore` 的 `*-local.yml` 规则排除；也可以完全不用它，改为注入环境变量 `MYSQL_USERNAME` / `MYSQL_PASSWORD`。

## 运行

```bash
# 开发（默认 profile=dev，会打印 SQL）
D:/software/apache-maven-3.9.12/bin/mvn.cmd spring-boot:run

# 打包并运行
D:/software/apache-maven-3.9.12/bin/mvn.cmd clean package
java -jar target/knowledge-pilot.jar --spring.profiles.active=dev
```

> 上面用的是 `mvn.cmd`：Windows 上 Maven 的 bin 目录里同时有 `mvn`（shell 脚本）和 `mvn.cmd`，
> PowerShell 直接执行不带扩展名的 `mvn` 会把它当文档打开而报错。Git Bash / WSL 里则用 `mvn` 或 `./mvn`。
> 若已把 Maven 的 bin 加入 PATH，全部命令简写为 `mvn ...` 即可。

| 入口 | 地址 |
| --- | --- |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| 健康检查 | http://localhost:8080/actuator/health |
| 指标 | http://localhost:8080/actuator/metrics |

## 接口

### 用户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/users` | 用户注册（用户名 / 密码 / 邮箱） |

```bash
curl -X POST http://localhost:8080/api/users -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","password":"abc123","email":"zhangsan@example.com"}'
# => {"code":0,"message":"ok","data":{"id":1,"username":"zhangsan","email":"zhangsan@example.com","status":1,...}}
```

校验规则：`username` 3~64 位、`password` 6~64 位（明文仅用于传输，落库前 BCrypt 散列）、`email` 可空但需格式合法。响应体永远不含 `passwordHash` 与 `deleted`。

### 工作空间

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/workspaces` | 创建工作空间，并自动把创建者写为 `OWNER` 成员 |

```bash
curl -X POST http://localhost:8080/api/workspaces -H "Content-Type: application/json" \
  -d '{"name":"研发中心知识库","description":"存放核心架构规范与开发指南","creatorId":1}'
```

`creatorId` 目前**由请求体传入**（还没有鉴权，无法从登录态推导）；服务端会校验该用户存在且 `status=1`，然后在同一事务里写 `workspace` + `workspace_member(role='OWNER')` 两行。

除此之外没有其他接口 —— 查询、分页、修改、删除均为后续计划。

## 统一约定

### 响应体与错误码

所有接口返回 `Result<T>`（[Result.java](src/main/java/com/knowledgepilot/common/Result.java)）：`{code, message, data}`。

**错误码即 HTTP 状态码**：`code` 与本次响应的 HTTP 状态码始终一致，成功固定为 `0`。前端按 HTTP 状态码分支即可，不存在第二套业务码（`ErrorCode` 只保留 `SUCCESS` 与 400/401/403/404/409/500 这些通用码）。若将来需要更细粒度的业务码，正确做法是给 `Result` 新增独立的 `bizCode` 字段，而不是复用 `code`。

`data` 为 null 时不会出现在 JSON 里（全局 `default-property-inclusion: non_null`）。

### 异常处理

`GlobalExceptionHandler` 把所有异常统一转成 `Result` + 对应 HTTP 状态码：

| 异常 | 状态码 | 说明 |
| --- | --- | --- |
| `BizException` | 其 `code` | 业务异常，400/401/403/404/409；5xx 级别会记 ERROR 全栈日志 |
| `MethodArgumentNotValidException` | 400 | `@Valid @RequestBody` 上的 JSR-380 校验失败 |
| `HandlerMethodValidationException` | 400 | Spring 6.1+ 起 `@Valid` 作用于 `@RequestParam`/`@PathVariable` 等方法参数时的失败 |
| `ConstraintViolationException` | 400 | 普通 Bean 方法参数上的约束校验失败 |
| `HttpMessageNotReadableException` | 400 | 请求体不是合法 JSON（不透传解析器细节） |
| `MethodArgumentTypeMismatchException` | 400 | 路径/查询参数类型不匹配 |
| `NoResourceFoundException` | 404 | 接口不存在 |
| `Exception` | 500 | 兜底，日志留全栈、响应只给"服务器内部错误" |

### 逻辑删除与审计字段

| 表 | 删除策略 | 原因 |
| --- | --- | --- |
| `user_account` | 逻辑删除（`deleted` 0/1） | 账号是核心资产，删除需留痕、可恢复 |
| `workspace` | 逻辑删除 | 空间下挂成员与后续的文档，需可恢复 |
| `workspace_member` | **物理删除** | 成员关系是纯关联，无需留痕；且软删会与 `uk_ws_user` 唯一索引打架 —— 退出（置 1）后重新加入会因旧行仍在而 INSERT 冲突 |

`deleted` 由全局配置 `logic-delete-field: deleted` 驱动：自动生成的查询追加 `deleted = 0`，`deleteById` 变成 `UPDATE deleted = 1`。**手写 SQL / 注解 SQL 不受此影响**，需要看到墓碑行时必须显式写 `deleted = 1`（见下）。

`create_time` / `update_time` 由 `AuditFieldHandler` 自动填充（`strictInsertFill` / `strictUpdateFill`，仅在值为 null 时写入），库表 `DEFAULT CURRENT_TIMESTAMP` 兜底。

### 逻辑删除的已知约束：墓碑占位

`user_account` 同时有逻辑删除和唯一索引 `uk_username` / `uk_email`，而唯一索引是物理约束 —— **被软删的账号（墓碑行）依然占着这个用户名/邮箱，无法被重新注册**。

服务端对此做了显式处理（[UserAccountServiceImpl.java](src/main/java/com/knowledgepilot/service/impl/UserAccountServiceImpl.java)）：注册链路分三层校验 —— 活跃账号查重（"用户名已存在"）→ 墓碑查询（[UserAccountMapper.java](src/main/java/com/knowledgepilot/mapper/UserAccountMapper.java) 用原生 `@Select` 绕过 `deleted = 0`，命中则明确告知"已被一个已注销的账号占用，请联系管理员恢复或清理"）→ 唯一索引兜底并发。这样用户不会收到指错方向的 409。

若将来希望"注销即可释放用户名"，需要改表结构（例如让 `deleted` 存删除时间/自增 id 而非常数 1，使唯一索引在多条墓碑行之间也成立），属于后续设计，当前不做。

### 其他防线

- `PaginationInnerInterceptor`：单页上限 200 条，`overflow=false`（页码超界返回空页而非第一页）。
- `BlockAttackInnerInterceptor`：拦截没有 where 条件的 update/delete。
- 口令：只经 BCrypt 散列后写入 `password_hash`，任何响应与日志都不回显明文或散列值；仓库内不含任何真实口令。

## 配置

`application.yml` 放公共配置，`application-dev.yml` / `application-prod.yml` 放环境差异，连接信息全部可用环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | 激活的 profile |
| `SERVER_PORT` | `8080` | 服务端口 |
| `MYSQL_HOST` / `MYSQL_PORT` | `127.0.0.1` / `3306` | MySQL 地址 |
| `MYSQL_DATABASE` | `knowledge_pilot` | 库名 |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 无默认值 | 业务账号；dev 走 `application-dev-local.yml`，prod 必须用环境变量 |
| `SWAGGER_ENABLED` | prod 为 `false` | 是否暴露 Swagger |
| `DB_POOL_MAX` / `DB_POOL_MIN` | prod 为 `20` / `5` | Hikari 连接池上下限（仅 prod 可调） |
| `LOG_FILE` | prod 为 `logs/knowledge-pilot.log` | 日志文件路径（仅 prod 写文件） |

prod profile 关闭 SQL 打印与 Swagger、健康检查不返回明细、日志写文件并滚动（50MB / 30 天）。

## 目录结构

```
src/main/java/com/knowledgepilot
├── KnowledgePilotApplication.java   # 入口，@MapperScan("com.knowledgepilot.mapper")
├── common/       Result（统一响应）、ErrorCode、BizException、GlobalExceptionHandler
├── config/       MybatisPlusConfig（分页/防全表更新）、AuditFieldHandler（审计填充）、
│                 OpenApiConfig、SecurityBeanConfig（PasswordEncoder）
├── controller/   REST 接口：UserAccountController、WorkspaceController
├── dto/          入参：UserCreateRequest、WorkspaceCreateRequest
├── entity/       表映射：UserAccount、Workspace、WorkspaceMember
├── mapper/       BaseMapper + 墓碑查询（UserAccountMapper 的两个 @Select）
├── service/      业务接口 + impl（事务边界在这一层）
└── vo/           出参：UserVO、WorkspaceVO（各带 from() 转换工厂，收口脱敏）

src/main/resources
├── application.yml / application-dev.yml / application-prod.yml
├── db/           init-database.sql（建库建号）、user_account.sql、workspace.sql、workspace_member.sql
└── mapper/       XML 映射目录（当前为空，全部走注解/通用 CRUD）

src/test/java/com/knowledgepilot
└── KnowledgePilotApplicationTests.java   # @SpringBootTest，会真实连库
```

## 数据库表

| 表 | 作用 | 关键设计 |
| --- | --- | --- |
| `user_account` | 账号 | `uk_username` / `uk_email` 唯一；`email` 可空（MySQL 唯一索引不约束 NULL，多个空邮箱不冲突）；`deleted` 软删 |
| `workspace` | 工作空间的容器与权限边界 | **没有 `owner_id`**：`creator_id` 只做审计且不可变，所有权唯一事实来源是成员表的 `role='OWNER'`；`idx_creator` 支撑"我创建的空间" |
| `workspace_member` | workspace 与 user 的多对多中间表 | `uk_ws_user(workspace_id, user_id)` 去重；`idx_user` 支撑"我加入的空间"；无 `deleted` 列（物理删除） |

三张表之间**只做逻辑关联，不建库级外键**；类型统一 `BIGINT UNSIGNED`，避免 JOIN 时符号位不匹配。建表语句由 `db/*.sql` 手工执行（`spring.sql.init.mode=never`），应用启动不会改表结构。

## 测试

```bash
# 只编译，不跑测试（不需要数据库）
D:/software/apache-maven-3.9.12/bin/mvn.cmd -DskipTests compile

# 跑测试：KnowledgePilotApplicationTests 是 @SpringBootTest，会真实连接 MySQL
# 需先保证容器已启动、库表已初始化，否则上下文启动失败
D:/software/apache-maven-3.9.12/bin/mvn.cmd test
```

除上下文加载外还没有业务测试；接口验证目前靠 Swagger UI 或 curl 手工执行。

## 已知限制与后续计划

- **无认证鉴权**：只引入了 crypto 模块，连 Security 过滤器链都没启用。因此 `creatorId` 只能由请求体传入，**任何人都能冒充任意用户创建空间**；这是当前阶段的已知缺口，接入登录鉴权的优先级最高。
- **无登录接口**：口令已用 BCrypt 落库，但尚无校验入口。
- **工作空间只有创建**：查询、更新、归档、成员增删改、所有权转让均未实现；`role` 目前也没有"每空间仅一个 OWNER"的约束。
- **文档域未开始**：知识库的核心实体（文档、分类、标签、检索）尚未建模。
- **表结构变更靠手工**：改表需自行补 SQL 脚本并手动执行，没有迁移工具（Flyway/Liquibase）。
