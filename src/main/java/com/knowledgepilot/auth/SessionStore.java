package com.knowledgepilot.auth;

import java.util.Optional;

/**
 * 登录会话存储接口（会话层的数据访问抽象）。
 *
 * <p>
 * 只定义“存、查、删”三个最小能力，隔离登录业务与具体存储实现：
 * 当前用内存 Map 即可满足 V0.1，将来会话量大或多实例部署需要外置存储（如 Redis）时，
 * 只换实现类，业务与鉴权代码零改动——技术按真实需求演进，不过早引入。
 * </p>
 *
 * <p>
 * 实现类必须保证线程安全（多请求并发读写）。
 * </p>
 */
public interface SessionStore {

    /**
     * 保存一个新会话。
     *
     * @param session 登录成功后构造的会话，调用方需保证非 null 且 token 未重复
     */
    void save(LoginSession session);

    /**
     * 按 token 查找会话。
     *
     * <p>
     * <b>契约：返回的会话一定是有效的（未过期）。</b>
     * 过期判断与清理由实现内部完成，调用方无需也不应再自行判断
     * {@code isExpired()}——把有效性收拢在存储层，可避免调用方遗漏判断导致的越权。
     * </p>
     *
     * <p>
     * 过期项的清理时机由实现决定（本项目采用"读取时惰性清理"），
     * 调用方不应依赖清理的及时性。
     * </p>
     *
     * @param token 客户端携带的登录令牌，允许为 null（视为未登录）
     * @return 有效会话；查不到、已过期或 token 为 null 时返回 {@link Optional#empty()}，不得返回 null
     */
    Optional<LoginSession> find(String token);

    /**
     * 按 token 删除会话（登出 / 主动踢下线时调用）。
     *
     * @param token 待删除的令牌；token 不存在时必须静默忽略，不得抛异常（保证登出幂等）
     */
    void remove(String token);
}
