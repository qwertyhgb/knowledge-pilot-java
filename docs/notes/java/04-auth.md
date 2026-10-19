# 登录鉴权（手写 Token + ThreadLocal）

> 记录时间：2026-10-17 起 | 对应代码：`src/main/java/com/knowledgepilot/auth/`
> 技术路线：**方案 B1 —— 手写 `HandlerInterceptor` + 随机 Token + 服务端会话存储**，
> 不引入 Spring Security 过滤器链；跑通后再用框架重构对比。
> 状态：✅ 已完成（第 1~7 步全部完成，2026-10-19）；端到端实测待 Docker / MySQL 可用后补做

---

## 一、我原本以为

> ⚠️ 这一栏留给你自己写。**必须是你真实的初始认知，哪怕是错的。**
> 下面给几个引导问题，想到什么写什么，不用都答。

1. 登录态到底是怎么"记住"的？前端每次请求怎么告诉服务端"我是谁"？
2. 为什么不能直接让前端传 `userId`？（我一开始是不是觉得这样挺方便的？）
3. `ThreadLocal` 是干什么用的？为什么不在方法参数里一路传下去？
4. 为什么 `ConcurrentHashMap` 不能存 `null`？
5. 会话过期后，服务端是怎么"知道"它过期的？需要定时任务吗？

（**开发完成后回来逐条修正**：写"错在哪 + 为什么会这么想"）

---

## 二、开发记录

### 2.1 分步计划（小步开发，每步可独立验证）

| 步 | 内容 | 文件 | 状态 |
|---|---|---|---|
| 1 | 值对象：`LoginUser` / `LoginSession` | 2 个 | ✅ |
| 2 | `TokenGenerator` + `SessionStore` 接口 | 2 个 | ✅ |
| 3 | `InMemorySessionStore` 实现 | 1 改 + 1 新 | ✅ |
| 4 | `UserContext`（ThreadLocal）+ `AuthInterceptor` | 2 个 | ✅ |
| 5 | `WebMvcConfig`（注册拦截器 + 白名单） | 1 个 | ✅ |
| 6 | `LoginRequest` / `LoginVO` / `AuthService(+Impl)` / `AuthController` | 5 个（拆成 6a 两个值对象 + 6b 三个链路文件） | ✅ |
| 7 | 身份改造：`WorkspaceMemberAddRequest`/`ServiceImpl` + `WorkspaceCreateRequest`/`ServiceImpl`（**验收标准**） | 4 个 | ✅ |

### 2.2 架构分层

```
LoginUser      → 身份层：当前操作的人是谁
LoginSession   → 会话层：这个凭证还有没有效
SessionStore   → 存储抽象（接口），当前内存实现，将来可换 Redis
TokenGenerator → 凭证生产（SecureRandom + Base64URL）
UserContext    → 请求级上下文（ThreadLocal）           ← 第 4 步
AuthInterceptor→ 把 token 翻译成可信身份               ← 第 4 步
```

### 2.3 关键设计决策

#### 决策 1：Token 用随机串，不用 JWT

- 随机串 + 服务端存储 = **有状态**
- JWT = **无状态**

**选择有状态的理由**：先在单机跑，将来上多实例时会真实遇到"用户在 A 机器登录，
请求打到 B 机器就 401"的问题 —— **这个痛点出现后再引 Redis 解决**，
才算"问题驱动技术演进"。若一上来就用 JWT，永远体会不到"为什么要共享会话"。

#### 决策 2：定义 `SessionStore` 接口，不直接用 Map

变化点隔离 —— 将来换 Redis 只改实现类，业务代码零改动。

#### 决策 3：过期判断放在 Store 内部（"返回即有效"）

两条路都合理：

| | A. Store 内部判（**本项目选这个**） | B. 调用方判 |
|---|---|---|
| `find()` 返回 | 一定有效 | 可能过期 |
| Store 职责 | 存储 + 生命周期 | 纯存储 |
| 防遗漏 | ✅ 架构上消灭 | ⚠️ 每个调用方都要记得 |

**选 A 的理由**：`find()` 将来有多个调用方（拦截器、登出、踢人），
**调用方越多，漏判过期的风险越大**。下沉到 Store 后这个风险从架构上消失。
类比 Redis 的 TTL —— 客户端不需要自己判过期。

#### 决策 4：会话不建表、不写库

内存 `ConcurrentHashMap` 足够 V0.1。已知局限（重启失效 / 多实例不共享 /
过期项可能长期驻留）**明确写进 Javadoc**，等真的影响使用再升级，
不为过渡实现过度设计。

---

## 三、我踩的坑

> ⚠️ 边做边记，别事后补

### 坑 1：`token == null && token.isEmpty()` 写成了 `&&` ❗

**现象**：`InMemorySessionStore.find()` 里判空写了 `&&`。

**根因**：`&&` 是**短路与**，左边为 `true` 时仍会求值右边。
传入 `null` 时，`token == null` 为 true → 继续执行 `token.isEmpty()` → **NPE**。

而且这个条件在逻辑上就自相矛盾：`null` 不可能同时是空串，
所以 `null && isEmpty()` **永远拦不住 null**。

**正确写法**：`if (token == null || token.isEmpty())`

**为什么危险**：编译期不报错，只有真的传 null 才爆。
而 `request.getHeader("Authorization")` 对未登录用户**确实会返回 null** ——
届时接口返回 500 而不是 401，错误语义完全错位。

**记忆法**：判空场景几乎永远用 `||`（任一满足就拦）。

---

## 四、面试怎么答

### 追问链 1：Token 与会话

#### Q1：登录态是怎么实现的？前端怎么传身份？

> A：前端登录成功拿到 token，之后每个请求在 `Authorization: Bearer <token>` 头里携带。
> 服务端拦截器解析 token → 查会话 → 得到可信的用户身份。
> 相比 Cookie-Session 的好处是**天然免疫 CSRF**（浏览器不会自动携带自定义 Header），
> 更适合前后端分离与移动端。

#### Q2：为什么用 Token 不用 Session？

> A：Session 依赖服务端会话存储，多实例需要共享；Token 可以做到无状态或独立存储。
> 核心权衡是：**有状态易撤销但难扩容，无状态易扩容但难踢人**。
> 我项目里选了有状态（随机串 + 服务端存），因为先要解决"可主动失效"。

#### Q3：Token 泄露了怎么办？

> A：我用的随机串 + 服务端存储，**天然支持主动失效**（删会话即可），
> 这是相对 JWT 的优势。JWT 想失效只能靠短有效期 + Refresh Token、
> 黑名单（每次请求查一次）、或版本号机制。

#### Q4（深挖）：多实例部署你这套会怎样？

> A：**会出问题** —— 用户在 A 机器登录，会话存在 A 的内存里；
> 请求被负载均衡到 B 机器时查不到会话 → 401。
> 解法是把 `SessionStore` 的实现从 `InMemorySessionStore` 换成 `RedisSessionStore`，
> **接口不变，业务零改动** —— 这就是把变化点隔离在实现层的价值。

### 追问链 2：并发容器与工具类

#### Q5：`HashMap` 和 `ConcurrentHashMap` 的区别？

> A：四点：
> 1. **线程安全**：CHM 用 CAS + synchronized 锁单个桶（JDK 8+），不是锁全表；
> 2. **null 限制**：CHM 不允许 null 键值，HashMap 允许；
> 3. **性能**：并发读几乎无锁，写只锁当前桶；
> 4. **复合操作原子性**：CHM 提供 `computeIfAbsent` / `computeIfPresent` / `merge`
>    这类原子复合操作，HashMap 没有。

#### Q6：CHM 为什么不允许 null 键值？（高频）

> A：**并发下 null 的语义有歧义**。
> `map.get(k)` 返回 null 时，到底是"键不存在"还是"值就是 null"？
> 单线程可以靠 `containsKey` 区分，但并发下这两步不是原子的，
> 中间状态会被其他线程改变，判断就失效了。所以干脆禁止 null。

#### Q7：那你为什么用 `computeIfPresent`，不 `get` 之后再 `remove`？

> A：`get` + `remove` 是**两步**，中间有窗口期，
> 且两次哈希查找多一次开销。`computeIfPresent` 在**同一个原子操作**内
> 完成"查找 + 判断 + 条件删除"（lambda 返回 null 即删除该键），
> 既保证原子性又省一次查找。
>
> ⚠️ 但有代价：它执行 lambda 时会**持有该桶的锁**，
> 所以 lambda 里绝对不能做耗时操作（查库、调接口），否则阻塞同桶的其他访问。

#### Q8：`SecureRandom` 和 `Random` 有什么区别？为什么 token 必须用前者？

> A：`Random` 是线性同余发生器，输出序列可由历史结果反推种子，
> **token 可被预测就意味着会话可被劫持**。
> `SecureRandom` 使用系统熵源，是密码学安全的。
>
> 另外两点实现细节：
> - 实例要**复用作字段**，反复 new 会重复播种、消耗熵池；
> - 编码要用 **URL 安全的 Base64**（`getUrlEncoder().withoutPadding()`），
>    否则产生 `+` `/` `=` 在 Header/URL 里需要转义。

### 追问链 3：`@Component` vs `@Service`

#### Q9：这两个注解有什么区别？

> A：**功能上没区别**（`@Service` 本身被 `@Component` 标注），
> 区别在**语义表达**：`@Service` 表示"我在业务服务层"，
> `@Component` 表示"我是个通用组件"。
> `TokenGenerator`、`InMemorySessionStore` 都用 `@Component`，
> 因为它们是技术设施不是业务服务 —— 换个项目也能直接用。

### 🎤 简历亮点话术

> "KnowledgePilot 的鉴权我没有直接用 Spring Security 过滤器链，
> 而是先手写了一遍 `HandlerInterceptor` + 随机 Token + `ThreadLocal` 的实现，
> 把登录态传递、`ThreadLocal` 清理、会话存储抽象、
> 登录接口防用户枚举这些点都实际踩过；
> 会话存储我定义成接口而不是直接用 Map，因为预判到多实例部署时要换成 Redis。
> 跑通后再用 Spring Security 重构对比，才真正知道框架帮我做了什么。"

---

## 五、❓ 待解答 / 以后要验证

- [ ] `ThreadLocal` 到底为什么会内存泄漏？key 是弱引用，value 是强引用，具体怎么泄漏的？
- [ ] 如果用户登录后一直不访问，会话会一直占内存 —— 什么量级下需要真的引入定时清理？
- [ ] 同一个账号多端登录，要不要互踢？后踢前、还是一起在线？（产品决策）
- [ ] Token 有效期设 2 小时合理吗？要不要加 Refresh Token？
- [ ] 拦截器里"无 token 直接放行"和"无 token 直接 401"两种策略，哪种更好？
- [ ] 密码错误时我跑了一次假散列来抵抗时序攻击，这个做法真的有效吗？（需要实测耗时）
- [ ] 将来换 Redis 时，`SessionStore` 接口需要改吗？过期交给 Redis 的 TTL 后，`find` 的语义还成立吗？
- [ ] 口令超过 72 字节（约 25 个汉字）时 BCrypt 抛 `IllegalArgumentException` → 500，当前按"已知边界"接受。
      正确处理方式三选一待定：限定 ASCII 字符集 / Service 里做字节守卫 / 自定义约束注解。

---

## 六、还没学到

- [ ] `HandlerInterceptor` 与 `Servlet Filter` 的区别与执行顺序
- [ ] `ThreadLocal` 的底层结构（ThreadLocalMap、开放寻址法）
- [ ] Spring Security 的 `SecurityFilterChain` 机制（第 8 步重构时学）
- [ ] JWT 的签名算法（HS256 / RS256）与验签流程
- [ ] 限流（登录失败次数限制）怎么实现
- [ ] CORS 与鉴权 Header 的配合

---

## 七、和项目的关系

> 本节让笔记和 KnowledgePilot 真实业务挂钩，避免变成脱离实践的抄书笔记。

| 关联点 | 说明 |
|---|---|
| 修正的真实漏洞 | `WorkspaceMemberAddRequest` 原有 `operatorId` 字段由**请求体传入**，任何人可冒充 OWNER 加成员。本次改造后改为从 `UserContext` 取，前端无法伪造 |
| 验收标准 | 第 7 步完成后，`addWorkspaceMember` 的操作者身份只能来自 token 解析结果 |
| 牵涉的既有代码 | `WorkspaceMemberServiceImpl.addWorkspaceMember`（第 6 步步骤 3 的权限校验）、`WorkspaceService.createWorkspace`（`creatorId` 同样应改为从上下文取） |
| 安全关联 | 项目已在 2026-10-16 修过"先鉴权后校验目标用户，消除用户枚举"（commit `3f50465`），登录接口的"统一错误提示"是同一思路的延续 |
| 将来演进 | 多实例部署时 `InMemorySessionStore` → `RedisSessionStore`，这正是技术路线图里 Redis 的**真实引入动机**（不是为简历而加） |
