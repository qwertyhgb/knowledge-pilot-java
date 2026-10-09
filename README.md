# KnowledgePilot

Spring Boot 4 + MyBatis-Plus + MySQL 的知识库服务骨架，开箱即用地跑通 Web / ORM / 分页 / 逻辑删除 / 参数校验 / 统一响应 / Swagger UI / 健康检查。

## 技术栈

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Spring Boot | 4.1.1 | `spring-boot-starter-parent` |
| Java | 21 | `java.version=21`，本机 `D:\software\jdk-21.0.8` |
| Maven | 3.9.12 | 本机 `D:\software\apache-maven-3.9.12` |
| MyBatis-Plus | 3.5.17 | `mybatis-plus-spring-boot4-starter`（Boot 4 专用）+ `mybatis-plus-jsqlparser` |
| MySQL | 8.4 | Docker 容器 `knowflow-mysql`，`3306:3306`，驱动 `mysql-connector-j` 9.7.0（Boot 管理） |
| springdoc-openapi | 3.1.0 | `springdoc-openapi-starter-webmvc-ui` |
| Lombok | 1.18.46 | 由 Boot 管理，打包时从 jar 中排除 |

## 前置条件

数据库使用本机 Docker 里已有的 MySQL 实例，**只需初始化一次**。仓库里不保存任何口令，请按下面的方式取用：

```bash
# 1. 启动容器（该容器 restart policy 为 no，机器重启后需要重新启动）
docker start knowflow-mysql

# 2. 建库 + 建业务账号：root 口令从容器环境变量取，不落盘
#    执行前先编辑 src/main/resources/db/init-database.sql，把 IDENTIFIED BY 的占位值改成本机口令，且不要提交该改动
docker exec -i knowflow-mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' \
  < src/main/resources/db/init-database.sql

# 3. 建表（口令交互式输入）
docker exec -it knowflow-mysql mysql -uknowledge_pilot -p knowledge_pilot \
  < src/main/resources/db/schema.sql

# 4. 让应用拿到口令：复制模板后填入第 2 步设置的口令
cp application-dev-local.yml.example application-dev-local.yml
```

`application-dev-local.yml` 已被 `.gitignore` 排除；也可以完全不用它，改为注入环境变量 `MYSQL_USERNAME` / `MYSQL_PASSWORD`。

## 运行

```bash
# 开发（默认 profile=dev）
D:/software/apache-maven-3.9.12/bin/mvn spring-boot:run

# 打包并运行
D:/software/apache-maven-3.9.12/bin/mvn clean package
java -jar target/knowledge-pilot.jar --spring.profiles.active=dev
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- 健康检查: http://localhost:8080/actuator/health

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/docs` | 分页查询（`page`/`size`/`keyword`/`category`/`status`） |
| GET | `/api/docs/{id}` | 查询单个文档 |
| POST | `/api/docs` | 新增 |
| PUT | `/api/docs/{id}` | 修改 |
| DELETE | `/api/docs/{id}` | 逻辑删除 |
| GET | `/api/docs/stats` | 按分类统计（XML 映射示例） |

```bash
curl -X POST http://localhost:8080/api/docs -H "Content-Type: application/json" \
  -d '{"title":"第一个文档","content":"hello","category":"guide","tags":"a,b","status":1}'
```

## 配置

`application.yml` 放公共配置，`application-dev.yml` / `application-prod.yml` 放环境差异；数据源连接信息均可用环境变量覆盖，无需改代码：

| 环境变量 | 默认值（dev） | 说明 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | 激活的 profile |
| `SERVER_PORT` | `8080` | 服务端口 |
| `MYSQL_HOST` / `MYSQL_PORT` | `127.0.0.1` / `3306` | MySQL 地址 |
| `MYSQL_DATABASE` | `knowledge_pilot` | 库名 |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 无默认值 | 业务账号；dev 走 `application-dev-local.yml`，prod 必须用环境变量 |
| `SWAGGER_ENABLED` | prod 为 `false` | 是否暴露 Swagger |

prod profile 关闭 SQL 打印与 Swagger、健康检查不返回明细、日志写 `logs/knowledge-pilot.log`。

## 目录结构

```
src/main/java/com/knowledgepilot
├── KnowledgePilotApplication.java   # @MapperScan 入口
├── common/       ApiResponse、BizException、GlobalExceptionHandler
├── config/       MybatisPlusConfig（分页/防全表更新）、AuditFieldHandler（自动填充）、OpenApiConfig
├── controller/   REST 接口
├── dto/          入参、查询条件、分页与统计结果
├── entity/       表映射对象
├── mapper/       BaseMapper + 自定义方法
└── service/      业务逻辑与事务
src/main/resources
├── application*.yml
├── db/           init-database.sql、schema.sql
└── mapper/       XML 映射
```

## 说明

- `KnowledgePilotApplicationTests` 是 `@SpringBootTest`，会真实连接 MySQL，运行测试前需保证容器已启动、库表已初始化；只想编译可用 `-DskipTests`。
- 建表语句由 `db/schema.sql` 手工执行（`spring.sql.init.mode=never`），避免应用启动改表结构。
- 本机 JDK 为 Oracle OpenJDK 21.0.8（`D:\software\jdk-21.0.8`），语言级别按 Java 21 编译；若要求 Temurin 发行版，装上后把 `JAVA_HOME` 指过去即可，项目无需改动。
- 仓库不含任何口令：`application-dev.yml` 里数据源账号密码是无默认值的占位符，真实口令只存在于本机未提交的 `application-dev-local.yml` 或环境变量中。忘了配口令时启动会报 access denied，属预期行为。
