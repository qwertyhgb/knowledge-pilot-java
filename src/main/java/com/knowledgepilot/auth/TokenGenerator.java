package com.knowledgepilot.auth;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * 登录令牌生成器。
 *
 * <p>
 * 生成密码学强随机的会话令牌：32 字节（256 位）熵量足以对抗暴力猜测；
 * 必须用 {@link SecureRandom} 而非 {@code Random}——后者输出可由历史结果反推种子，token 可预测即会话可劫持。
 * 采用 URL-safe Base64（输出 {@code -_} 而非 {@code +/}）且去填充，token 无需转义即可放进 Header / Cookie / URL，
 * 长度固定 43 字符。
 * </p>
 */
@Component
public class TokenGenerator {

    /** 熵源字节数：32 字节 = 256 位，Base64 无填充编码后固定 43 字符 */
    private static final int TOKEN_BYTES = 32;

    /** 线程安全，@Component 单例共享即可；实例化有熵获取开销，不每次 new */
    private final SecureRandom secureRandom = new SecureRandom();

    /** Base64.Encoder 无状态且线程安全，不依赖实例状态，声明为常量 */
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /**
     * 生成一个新令牌。
     *
     * @return 43 字符的 URL-safe Base64 字符串，全局唯一性由随机熵保证（256 位空间碰撞概率可忽略）
     */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
