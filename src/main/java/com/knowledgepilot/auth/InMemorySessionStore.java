package com.knowledgepilot.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * {@link SessionStore} 的内存实现（V0.1）。
 *
 * <p>
 * 用 {@link ConcurrentHashMap} 存会话：多请求并发读写安全，无需额外加锁。
 * 过期会话采用惰性删除：仅在 {@code find} 命中时顺带清理，不做全局扫描，避免把 O(n) 开销挂在热点路径上。
 * </p>
 *
 * <p>已知局限（真实需求出现时再升级，不提前引入外部存储）：
 * 会话存在单 JVM 内存中，重启即丢失、多实例间不共享；这两点对 V0.1 本地开发均可接受。</p>
 */
@Component
public class InMemorySessionStore implements SessionStore {

    /** token → 会话；选 ConcurrentHashMap 是因为 save/find/remove 会在多请求线程间并发执行 */
    private final ConcurrentHashMap<String, LoginSession> sessions = new ConcurrentHashMap<>();

    /** 同 token 再次登录直接覆盖旧会话，等价于“新登录顶替旧登录” */
    @Override
    public void save(LoginSession session) {
        sessions.put(session.token(), session);
    }

    /**
     * 查找并顺带清理过期会话，保证“返回即有效”。
     *
     * <p>{@code computeIfPresent} 的回调返回 null 时 Map 会原子地删除该条目，
     * 因此“判过期 + 删除”在同一个原子操作内完成，不会出现先查后删的竞态窗口。</p>
     */
    @Override
    public Optional<LoginSession> find(String token) {
        // 空 token 直接短路，避免拿无意义的 key 去碰 Map
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }

        LoginSession session = sessions.computeIfPresent(token,
                (key, value) -> value.isExpired(Instant.now()) ? null : value);

        return Optional.ofNullable(session);
    }

    /** 登出：token 不存在时静默忽略，保证登出幂等 */
    @Override
    public void remove(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

}
