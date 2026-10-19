package com.knowledgepilot.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import com.knowledgepilot.common.BizException;
import com.knowledgepilot.entity.UserAccount;
import com.knowledgepilot.mapper.UserAccountMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录鉴权拦截器（Spring MVC 的全局闸机）。
 *
 * <p>
 * 职责：对每一个命中拦截路径的请求完成“验票 + 核对身份 + 绑定上下文”，
 * 把鉴权从业务代码里彻底抽出来，Controller / Service 不需要再关心 token 和会话。
 * 本类是把 {@link TokenGenerator}、{@link SessionStore}、{@link LoginSession}、
 * {@link LoginUser}、{@link UserContext} 串起来的关键一环。
 * </p>
 *
 * <p>
 * 处理链路：解析 Authorization 头 → 查会话（已保证“返回即有效”）→ 回表确认用户存在且启用
 * → 写入 {@link UserContext} → 放行。任一步失败直接抛 {@link BizException}，
 * 由 GlobalExceptionHandler 统一转换为 401 / 403 的 Result 响应。
 * </p>
 *
 * <p>
 * 注意：本类必须通过 {@code WebMvcConfigurer#addInterceptors} 注册后才生效，
 * 且登录 / 注册等白名单路径必须排除，否则用户永远拿不到 token（死循环）。
 * </p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 认证头名，RFC 7235 标准头 */
    private static final String AUTH_HEADER = "Authorization";

    /** Bearer 认证方案前缀（RFC 6750，最常见于 JWT / 不透明 token），注意末尾带一个空格 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 账号启用状态值：1-启用（对应 user_account.status，V0.1 未上枚举，故用常量避免魔法值） */
    private static final int STATUS_ENABLED = 1;

    /** 会话存储：验票依据 token 是否在存、是否过期 */
    private final SessionStore sessionStore;

    /** 用户表访问：会话只存了 userId，用户是否被删、是否被封禁必须回表确认 */
    private final UserAccountMapper userAccountMapper;

    /**
     * 构造器注入。
     *
     * <p>依赖声明为 final + 构造器注入：依赖不可变、不可为空，且单构造器无需 @Autowired。
     * 拦截器本身无状态，作为 @Component 单例被所有请求线程共享是安全的。</p>
     *
     * @param sessionStore      会话存储，用于校验 token
     * @param userAccountMapper 用户表访问，用于校验用户存在性与封禁状态
     */
    public AuthInterceptor(SessionStore sessionStore, UserAccountMapper userAccountMapper) {
        this.sessionStore = sessionStore;
        this.userAccountMapper = userAccountMapper;
    }

    /**
     * 请求进入 Controller 之前执行：验票 + 绑定身份。
     *
     * <p>注意：若本方法抛异常，Spring 不会调用 {@code afterCompletion}，
     * 因此必须在方法开头先 {@link UserContext#clear()}，避免鉴权失败时把线程池线程上的
     * 脏身份遗留给下一个请求。</p>
     *
     * @return true 放行（会话合法、用户正常）；返回 false 会直接结束请求，本类不用，失败统一抛异常
     * @throws BizException 缺 token / 会话失效 / 用户被删（401），或账号被封禁（403）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        // ① 先清残留：线程池复用线程，上一请求若未清理，这里先抹掉脏数据
        UserContext.clear();

        // ② 解析 token：支持 "Bearer xxx" 与裸 token 两种写法
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            throw BizException.unauthorized("未登录或凭证缺失");
        }

        // ③ 查会话：find 遵守契约“返回即有效”，已过期 / 查不到均返回空，无需再判过期
        LoginSession session = sessionStore.find(token).orElseThrow(() -> BizException.unauthorized("登录状态已失效，请重新登录"));

        // ④ 回表确认用户仍存在：用户被物理删除后，会话里残留的 userId 不能再生效
        UserAccount user = userAccountMapper.selectById(session.userId());
        if (user == null) {
            sessionStore.remove(token);
            throw BizException.unauthorized("登录用户不存在，请重新登录");
        }
        // ⑤ 校验封禁状态：每请求查库保证封禁立即生效，代价是每请求一次 DB 查询（V0.1 可接受的取舍）
        if (user.getStatus() != null && user.getStatus() != STATUS_ENABLED) {
            sessionStore.remove(token);
            throw BizException.forbidden("账号已被禁用");
        }

        // ⑥ 绑定身份：只放 id 与 username，不带密码散列等敏感字段
        UserContext.set(new LoginUser(user.getId(), user.getUsername()));
        return true;
    }

    /**
     * 请求处理完成后的回调（相当于拦截器链的 finally）。
     *
     * <p>仅在 {@code preHandle} 返回 true 后才会被调用，且业务抛异常也会执行，
     * 因此这里是清理 {@link UserContext} 的可靠位置，防止线程池复用导致身份泄漏。</p>
     *
     * @param ex 处理过程中抛出的异常，正常结束为 null
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            @Nullable Exception ex) throws Exception {
        UserContext.clear();
    }

    /**
     * 从 Authorization 头解析 token。
     *
     * <p>宽松解析：带 {@code Bearer } 前缀则去掉前缀，不带则按裸 token 使用（方便本地 curl 调试）。
     * 同一请求只服务一个 token，因此头部出现多个值时只取第一个。</p>
     *
     * @param request 当前 HTTP 请求
     * @return 令牌字符串；头缺失或为空返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (!StringUtils.hasText(header)) {
            return null;
        }
        return header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()).trim() : header.trim();
    }
}
