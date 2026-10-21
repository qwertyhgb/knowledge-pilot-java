# KnowledgePilot

Spring Boot 4 + MyBatis-Plus + MySQL 的知识库服务，开箱即用地跑通 Web / ORM / 参数校验 / 统一响应与异常 / 逻辑删除 / 审计字段自动填充 / Swagger UI / 健康检查。

> **当前进度（V0.1）**：基础设施与分层骨架已成型，共 **9 个接口**：**用户注册**、**登录 / 登出**、
> **创建工作空间**、**分页查询我加入的空间**、**更新工作空间**、**归档 / 恢复工作空间**、**删除工作空间**、**添加空间成员**；
> 登录鉴权已闭环并通过端到端实测（2026-10-20）。
> 文档域（知识库真正的核心）还没开始，接口清单里没有的就是没有。

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

> 除 `POST /api/users`（注册）与 `/api/auth/**` 外，所有接口都需要在请求头带 `Authorization: Bearer <token>`，否则返回 401。详见「鉴权」一节。

### 登录鉴权

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 用户名 + 密码换取访问令牌 |
| POST | `/api/auth/logout` | 使当前令牌失效（幂等：无效或缺失 token 也返回成功） |

```bash
# 登录：后续接口用返回的 data.token 作通行证
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","password":"abc123"}'
# => {"code":0,"message":"ok","data":{"token":"<43 字符>","expiresAt":"2026-10-19T09:15:00Z","userId":1,"username":"zhangsan"}}

# 登出
curl -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer <token>"
```

令牌有效期 2 小时（`AuthServiceImpl.SESSION_TTL`，当前是常量）。登录失败统一返回 401「用户名或密码错误」，**不区分**"用户不存在"与"密码错误"（防账号枚举）；密码正确但账号被禁用返回 403。同一账号可多端同时在线，不互踢。

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
| GET | `/api/workspaces` | 分页查询当前用户加入的工作空间（`page` / `size`，身份取自登录会话） |
| PUT | `/api/workspaces/{workspaceId}` | 更新空间名称与简介（PUT 全量语义），仅该空间 OWNER 可操作 |
| PUT | `/api/workspaces/{workspaceId}/status` | 归档 / 恢复空间（`status`：0-归档停用，1-正常），仅该空间 OWNER 可操作，幂等 |
| DELETE | `/api/workspaces/{workspaceId}` | 逻辑删除空间（`deleted=1`），仅该空间 OWNER 可操作；归档空间也可删 |

```bash
# 创建：创建者取自登录会话，请求体不再接受 creatorId
curl -X POST http://localhost:8080/api/workspaces \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" \
  -d '{"name":"研发中心知识库","description":"存放核心架构规范与开发指南"}'

# 分页查询：身份取自登录会话，不传 memberId；page 默认 1，size 默认 20、上限 200
curl -H "Authorization: Bearer <token>" "http://localhost:8080/api/workspaces?page=1&size=20"
# => {"code":0,"message":"ok","data":{"total":1,"pageNum":1,"pageSize":20,"pages":1,"records":[...]}}

# 更新：PUT 全量语义，name 必填；description 传 null 表示清空简介；仅该空间 OWNER 可操作
curl -X PUT http://localhost:8080/api/workspaces/1 \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" \
  -d '{"name":"研发中心知识库（更名）","description":null}'

# 归档 / 恢复：把状态置为目标值（0=归档停用，1=正常）；仅该空间 OWNER 可操作
# 幂等：重复提交同一目标状态返回成功且不写库（updateTime 不变）
curl -X PUT http://localhost:8080/api/workspaces/1/status \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" \
  -d '{"status":0}'

# 删除：逻辑删除（UPDATE deleted=1），不是物理抹掉；仅该空间 OWNER 可操作
# 归档空间也可删；再次删除已不存在的空间返回 404
curl -X DELETE http://localhost:8080/api/workspaces/1 \
  -H "Authorization: Bearer <token>"
```

创建者身份**取自登录会话**（`UserContext`），请求体不接受 `creatorId`——否则任何人都能以他人名义建空间，并因"创建即写入 OWNER 成员"而凭空获得该空间的最高权限。服务端在同一事务里写 `workspace` + `workspace_member(role='OWNER')` 两行。

查询主体**取自登录会话**（`UserContext`），接口不接受 `memberId`——即使带 `?memberId=999` 也会被忽略。原 `memberId` 参数可越权查看任意用户空间（IDOR），已于 2026-10-20 修复并实测验证。

更新操作者身份同样**取自登录会话**（`UserContext`），且必须是该空间 OWNER、空间未归档（403）。采用 **PUT 全量语义**：`name` 必填并覆盖原值；`description` 传 `null` 或空白串表示清空简介——落库用**显式 `set` 写 `null`**，而非 `updateById`（后者会静默跳过 null 字段，"清空"会失效）。请求体不接受 `creatorId` / `status`，客户端无法借此改写归属或"复活"归档空间。

**归档空间的口径（产品决策）**：`status=0`（已归档）的空间**仍然出现在列表里**，由前端据 `status` 自行置灰或折叠；但**不允许向归档空间添加成员或更新空间信息**（403）。也就是"看得见、改不了"——归档是可恢复状态，直接从列表过滤会让用户找不到自己的空间。允许作用在归档空间上的写操作只有两类：归档 / 恢复（状态机自身）和删除（生命周期销毁）。

状态变更的唯一入口是 `PUT /api/workspaces/{workspaceId}/status`，语义是**把状态置为目标值**，因此**幂等**：归档两次、恢复两次都返回成功，重复提交不写库（`updateTime` 不变）。恢复（0→1）必须能操作归档态，否则空间一旦归档就永久锁死；这与「归档不可改内容」不矛盾：约束的是内容修改，不是状态机自身。并发下用 CAS（`WHERE id = ? AND status = 期望旧值`）保证同一时刻只有一个请求生效，未打中时重读区分「已达目标（按幂等成功返回）/ 已被删除（404）/ 变成其他状态（409）」。请求体只接受 `status`，没有 `name` / `creatorId` / `deleted`。

删除走 `DELETE /api/workspaces/{workspaceId}`：**逻辑删除**（实体 `@TableLogic`，`removeById` 生成 `UPDATE deleted=1 WHERE id=? AND deleted=0`），不是物理抹掉。仅该空间 OWNER 可操作；归档空间也可删，否则归档后只能恢复、无法销毁。成员行**不级联删除**——空间按可恢复设计，成员关系应留下来；`workspace_member` 本身是物理删除表，这里若顺手删成员，恢复后会变成空壳。再次删除同一空间返回 **404**（`getById` 自动过滤已删行），与登出“重复也成功”不同：删除的对象是资源本身。并发下两个请求只有一个 `deleted=0` 打中，未打中按 404 返回。

### 空间成员

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/workspaces/{workspaceId}/members` | 把指定用户加入空间，操作者必须是该空间 OWNER |

```bash
# 操作者取自登录会话，请求体只传被添加的人与角色
curl -X POST http://localhost:8080/api/workspaces/1/members \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" \
  -d '{"userId":2,"role":"MEMBER"}'
```

校验链路：空间存在且未归档 → **操作者是该空间 OWNER**（身份来自 `UserContext`，无法伪造；先鉴权）→ 操作者账号未被禁用 → 被添加用户存在且账号启用 → 角色只能是 `MEMBER`（空间所有者唯一，不允许再指定 OWNER）→ 未被重复添加（`uk_ws_user` 唯一索引兜底并发）。

> **顺序说明**：把"鉴权"排在"被添加用户是否存在"之前是刻意的。反过来的话，任何无权限的调用者都能利用 404（用户不存在）与 403（无权限）的错误码差异，枚举出全站哪些 `userId` 真实存在。

## 鉴权

- **拦截范围**：`AuthInterceptor` 只作用于 `/api/**`；白名单为 `POST /api/users`（注册）、`POST /api/auth/login`、`POST /api/auth/logout`。
- **携带方式**：请求头 `Authorization: Bearer <token>`；为便于本地调试，也接受不带 `Bearer ` 前缀的裸 token。
- **身份传递**：校验通过后把 `LoginUser` 写入 `UserContext`（ThreadLocal），业务层用 `UserContext.requireUserId()` 取当前用户；**操作者 / 创建者身份一律不接受请求体传入**。请求结束（含异常）在 `afterCompletion` 中清理，避免线程池复用导致身份泄漏。
- **每请求回表**：拦截器会按会话里的 `userId` 查一次用户，保证"注销 / 封禁立即生效"；代价是每个请求多一次主键查询。
- **状态码语义**：401 = 未登录或令牌失效/过期；403 = 已认证但无权（非空间成员、非 OWNER、账号被禁用）。登录失败同样是 401，且不区分"用户不存在"与"密码错误"。

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
| `MethodArgumentNotValidException` | 400 | `@Valid @RequestBody` 或 `@Valid @ModelAttribute` 上的 JSR-380 校验失败 |
| `BindException` | 400 | 数据绑定失败（如 `?page=abc`）；`MethodArgumentNotValidException` 是它的子类，会被上一行优先匹配 |
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

`deleted` 由全局配置 `logic-delete-field: deleted` 驱动：自动生成的查询追加 `deleted = 0`（**Wrapper 生成的 update 语句同样会追加**，已实测见 dev SQL 日志：`UPDATE workspace SET ... WHERE deleted=0 AND ...`），`deleteById` 变成 `UPDATE deleted = 1`。**手写 SQL / 注解 SQL 不受此影响**，需要看到墓碑行时必须显式写 `deleted = 1`（见下）。

`create_time` / `update_time` 的写入有**两条路径**：**插入**时由 `AuditFieldHandler` 的 `strictInsertFill` 填两列；**更新**时若走实体更新由 `strictUpdateFill` 填 `update_time`，而 `update(null, wrapper)`（如工作空间更新用的 `lambdaUpdate()`）不带实体、**不触发填充**，此时 `update_time` 由库端 `ON UPDATE CURRENT_TIMESTAMP` 写入。

**这要求"库端时区 = 应用时区"**：容器 MySQL 的系统时区是 UTC，而 `AuditFieldHandler` 用 JVM 本地时间；若不固定会话时区，同一行会出现 `create_time` 与 `update_time` 相差 8 小时（2026-10-20 实测确认，且表现为"更新时间比创建时间还早"）。已通过 JDBC 参数 `connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true` 让连接会话执行 `SET time_zone`，两者恢复一致。

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
│                 WebMvcConfig（注册鉴权拦截器）、OpenApiConfig、SecurityBeanConfig（PasswordEncoder）
├── auth/         鉴权基础设施：LoginUser、LoginSession、TokenGenerator、SessionStore、
│                 InMemorySessionStore、UserContext、AuthInterceptor
├── controller/   REST 接口：UserAccountController、AuthController、WorkspaceController、
│                 WorkspaceMemberController
├── dto/          入参：UserCreateRequest、LoginRequest、WorkspaceCreateRequest、
│                 WorkspacePageQuery、WorkspaceUpdateRequest、WorkspaceMemberAddRequest
├── entity/       表映射：UserAccount、Workspace、WorkspaceMember
├── mapper/       BaseMapper + 手写 SQL（UserAccountMapper 墓碑查询、WorkspaceMapper 跨表分页）
├── service/      业务接口 + impl：UserAccountService、AuthService、WorkspaceService、
│                 WorkspaceMemberService（事务边界在这一层）
└── vo/           出参：UserVO、LoginVO、WorkspaceVO、WorkspaceMemberVO、PageResult

src/main/resources
├── application.yml / application-dev.yml / application-prod.yml
├── db/           业务建表脚本：init-database.sql（建库建号）、user_account.sql、
│                 workspace.sql、workspace_member.sql
└── mapper/       XML 映射目录（当前为空，全部走注解/通用 CRUD）

src/test/java/com/knowledgepilot
└── KnowledgePilotApplicationTests.java   # @SpringBootTest，会真实连库

docs/notes/                                 # 学习笔记，详见该目录的 README
├── README.md          总索引（学习路线 + 各笔记进度）
├── _TEMPLATE.md       笔记骨架模板
└── mysql/             01-index.md（索引实验笔记）+ index-lab.sql（实验脚本）
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

- **鉴权是手写实现，不是 Spring Security**：`HandlerInterceptor` + 随机 token + 服务端会话，没有过滤器链、方法级权限注解或 RBAC。会话存在单 JVM 内存（`ConcurrentHashMap`）里：重启即丢失、多实例不共享（换成 Redis 实现即可，接口不变）；同一账号可多端同时在线，不互踢。
- **口令有 72 字节硬边界**：`@Size(max = 64)` 数的是**字符**，BCrypt 数的是**字节**；25 个汉字（75 字节）起会抛 `IllegalArgumentException`，最终返回 500 而不是 400。当前按"已知边界"接受，等真实用户出现再处理。
- **无登录限流**：登录失败次数不受限；且"用户不存在"分支也会跑一次 BCrypt（防时序枚举的代价），构成一个可被放大的 CPU 消耗面。
- **工作空间完成了创建、查询、更新、归档 / 恢复与逻辑删除**：移出成员、所有权转让、已删空间的恢复仍未实现。逻辑删除后成员行仍在（为可恢复预留），当前没有“恢复已删空间”的接口。"每空间仅一个 OWNER"目前靠添加成员接口的单一防线（只允许传 `MEMBER`）间接保证，**没有数据库级约束**。
- **文档域未开始**：知识库的核心实体（文档、分类、标签、检索）尚未建模。
- **表结构变更靠手工**：改表需自行补 SQL 脚本并手动执行，没有迁移工具（Flyway/Liquibase）。
