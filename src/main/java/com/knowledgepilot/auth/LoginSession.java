package com.knowledgepilot.auth;

import java.time.Instant;

/**
 * 登录会话（服务端持有的登录凭证记录）。
 *
 * <p>
 * 表示一次成功登录产生的会话：登录时生成 {@code token} 返给客户端并保存本对象，
 * 后续请求凭 token 找到会话，校验是否过期、还原用户身份。
 * 与 {@link LoginUser} 的分工：本对象管“凭证还有没有效”（会话层），
 * LoginUser 管“当前操作的人是谁”（身份层）。
 * </p>
 *
 * @param token     登录令牌，全局唯一；客户端每次请求携带，服务端据此定位会话
 * @param userId    会话归属的用户ID
 * @param expiresAt 会话过期时间点（UTC），过期后拒绝服务
 */
public record LoginSession(String token, Long userId, Instant expiresAt) {

    /**
     * 判断会话是否已过期。
     *
     * <p>时间由调用方传入而非内部取 {@code Instant.now()}，
     * 保证判断为纯函数，便于单测注入任意时刻、也让“过期语义”内聚在会话对象上。</p>
     *
     * @param now 当前时刻
     * @return 当前时刻晚于过期时间则视为已过期
     */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
