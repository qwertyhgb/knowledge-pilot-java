package com.knowledgepilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全相关 Bean 配置。
 *
 * <p>当前项目只引入了 spring-security-crypto（仅加密能力，无过滤器链），
 * 因此这里只负责装配口令编码器；以后接入完整登录鉴权时，
 * SecurityFilterChain 等组件也统一收口在本类中。</p>
 */
@Configuration
public class SecurityBeanConfig {

    /**
     * 全局口令编码器：BCrypt 散列。
     *
     * <p>BCrypt 特点：</p>
     * <ul>
     *   <li>自带随机盐：同一明文每次散列结果都不同，无需单独存盐</li>
     *   <li>单向不可逆，只能用 {@code matches(明文, 散列)} 比对</li>
     *   <li>默认强度 10（2^10 轮），兼顾安全与性能；强度越大越慢，越抗暴力破解</li>
     * </ul>
     *
     * <p>该对象无状态、线程安全，全局单例即可。
     * 声明为接口类型 {@link PasswordEncoder}，Service 层依赖接口而非实现，
     * 以后换算法（如升级 Argon2）只改这里一处。</p>
     *
     * @return BCrypt 口令编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}