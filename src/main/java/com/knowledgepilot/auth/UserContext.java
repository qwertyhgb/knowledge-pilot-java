package com.knowledgepilot.auth;

import com.knowledgepilot.common.BizException;

/**
 * 当前请求的用户上下文（ThreadLocal + 静态工具类）。
 *
 * <p>
 * 解决的问题：鉴权通过后，“当前用户是谁”需要在 Controller → Service → Mapper 的调用链中随时取用。
 * 如果靠方法参数逐层传递 {@link LoginUser}，每个下游方法都要被迫加一个自己用不到的参数；
 * ThreadLocal 把身份绑定到“当前请求线程”上，让业务代码通过 {@link #requireUserId()} 随时取到，
 * 而 Servlet 模型的约束是“一个请求 = 一个线程”，所以两者恰好契合。
 * </p>
 *
 * <p>
 * 强制约束：
 * <ul>
 *   <li>{@link #set(LoginUser)} 必须在鉴权通过后、进入业务逻辑前调用（通常在拦截器/过滤器中）；</li>
 *   <li>{@link #clear()} 必须在请求结束时于 {@code finally} 中调用。Servlet 容器线程池会复用线程，
 *       不清理会导致下个请求在未鉴权的情况下读到上一个用户的身份——这是 ThreadLocal 最经典的泄漏 Bug。</li>
 * </ul>
 * </p>
 *
 * <p>
 * 已知边界：ThreadLocal 不跨线程传递。业务里一旦开子线程 / 交给线程池 / 用 @Async，
 * 子线程里读不到上下文，也需要显式传递（V0.1 不处理，出现真实异步需求再说）。
 * </p>
 */
public final class UserContext {

    /**
     * 每线程一份的用户上下文。
     * 用 ThreadLocal 而非 InheritableThreadLocal：不希望子线程默认继承父线程身份，避免隐式“越权”。
     */
    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    /** 私有构造：纯静态工具类，禁止实例化 */
    private UserContext() {

    }

    /**
     * 绑定当前请求的用户。
     *
     * <p>user 为 null 抛 {@link IllegalArgumentException} 而非业务异常：
     * 传 null 是调用方的编程错误（忘了先鉴权？），不是用户可见的业务失败，应该尽早暴露而不是静默写入。</p>
     *
     * @param user 已认证的登录用户，不能为 null
     */
    public static void set(LoginUser user) {
        if (user == null) {
            throw new IllegalArgumentException("LoginUser 不能为null");
        }
        CONTEXT.set(user);
    }

    /**
     * 获取当前用户，可能为 null。
     *
     * <p>适用于“登录可选”的接口（如匿名访问列表），调用方必须自己判空；
     * 业务接口一律用 {@link #requireUserId()}，不要用 get() 后手动判空。</p>
     *
     * @return 当前用户；未登录 / 已登出时为 null
     */
    public static LoginUser get() {
        return CONTEXT.get();
    }

    /**
     * 获取当前登录用户 ID，未登录则直接抛 401。
     *
     * <p>强制登录接口的标准入口：在这里快速失败（fail-fast），业务代码就不用在每个方法里重复判空，
     * 抛出的 {@link BizException} 最终由 GlobalExceptionHandler 统一转为 401 Result。</p>
     *
     * @return 当前登录用户的主键 ID
     * @throws BizException 未登录（code = 401）时抛出
     */
    public static Long requireUserId() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw BizException.unauthorized("请先登录");
        }
        return user.id();
    }

    /**
     * 清空当前线程的用户上下文。
     *
     * <p>必须在请求结束的 {@code finally} 中无条件调用（即使业务抛异常也要执行），
     * 否则线程被线程池复用时，下一个请求会继承上一个用户的身份。</p>
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
