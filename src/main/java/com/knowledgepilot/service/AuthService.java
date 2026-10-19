package com.knowledgepilot.service;

import com.knowledgepilot.dto.LoginRequest;
import com.knowledgepilot.vo.LoginVO;

/**
 * 登录认证业务服务。
 *
 * <p>
 * 职责边界：只负责“凭证校验 + 会话建立/销毁”的业务编排，不处理 HTTP 协议细节
 * （由 Controller 负责），也不关心令牌如何生成、会话存在哪里
 * （分别由 {@link com.knowledgepilot.auth.TokenGenerator}、
 * {@link com.knowledgepilot.auth.SessionStore} 负责）。
 * </p>
 *
 * <p>
 * 与 {@link UserAccountService} 的分工：后者负责账号的 CRUD，本接口负责“认证”这一独立关注点；
 * 两者都读写 user_account，但业务语义不同，不要为了省事把登录塞进账号服务。
 * </p>
 *
 * <p>
 * 事务说明：登录是“读用户 + 写内存会话”，登出是纯内存删除，均不涉及多表写入，
 * 实现类不需要 {@code @Transactional}；不要为了“看起来规范”而盲目加事务。
 * </p>
 */
public interface AuthService {

    /**
     * 登录：校验凭证，成功后生成会话令牌。
     *
     * <p>
     * 处理链路：用户名规范化（trim）→ 按用户名查活跃账号 → BCrypt 比对口令
     * → 校验账号状态 → 生成 token → 保存会话 → 返回登录出参。
     * </p>
     *
     * <p><b>安全契约（实现时必须遵守）：</b></p>
     * <ul>
     *   <li>用户不存在与口令错误必须返回<b>同一条</b> 401 提示，禁止用不同文案区分——
     *       否则调用方可以枚举出哪些用户名真实存在；</li>
     *   <li>账号被禁用只能在口令校验<b>通过之后</b>判断并返回 403，否则同样构成用户枚举；</li>
     *   <li>口令只用于比对：禁止 trim、禁止落库、禁止写入日志或响应。</li>
     * </ul>
     *
     * <p><b>输入约束（与注册链路对齐，抄错会导致“能注册却登不上”）：</b></p>
     * <ul>
     *   <li>{@code username} 查库前必须 {@code trim()}，因为注册时是 trim 后落库的；</li>
     *   <li>{@code password} 必须原样参与 BCrypt 比对，空格是口令的一部分。</li>
     * </ul>
     *
     * @param request 登录请求，调用方需保证已通过 Bean Validation
     * @return 登录出参：令牌、会话过期时间、用户ID与用户名
     * @throws com.knowledgepilot.common.BizException 凭证错误（401）或账号被禁用（403）
     */
    LoginVO login(LoginRequest request);

    /**
     * 登出：使指定令牌对应的服务端会话失效。
     *
     * <p><b>幂等契约：</b>
     * token 为空、不存在或已过期时都必须静默返回，不得抛异常。
     * 登出接口已在鉴权白名单中——如果要求“必须持有有效 token 才能登出”，
     * 用户会话过期后就永远无法登出。
     * </p>
     *
     * <p>
     * 只负责删除服务端会话；客户端清除本地 token 由前端完成。本方法不涉及数据库操作。
     * </p>
     *
     * @param token 待失效的登录令牌，允许为 null
     */
    void logout(String token);
}
