package com.knowledgepilot.vo;

import com.knowledgepilot.entity.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 用户信息出参（View Object）。
 *
 * <p>只承载"可以给前端看"的字段，实体转 VO 时自动完成脱敏：
 * {@code passwordHash}（口令散列）和 {@code deleted}（逻辑删除标记）
 * 是内部字段，任何情况下都不能出现在响应里。</p>
 *
 * <p>用 record 定义：不可变、线程安全，构造器/访问器/equals 全部自动生成。
 * Jackson 从 Spring Boot 3 起原生支持 record 的序列化与反序列化。</p>
 */
@Schema(description = "用户信息")
public record UserVO(

        @Schema(description = "用户ID", example = "1")
        Long id,

        @Schema(description = "登录名", example = "zhangsan")
        String username,

        @Schema(description = "邮箱，可空", example = "zhangsan@example.com", nullable = true)
        String email,

        @Schema(description = "状态：0-禁用，1-启用", example = "1")
        Integer status,

        @Schema(description = "创建时间")
        LocalDateTime createTime,

        @Schema(description = "最后修改时间")
        LocalDateTime updateTime
) {

    /**
     * 实体 → VO 的转换工厂。
     *
     * <p>把"从 UserAccount 摘哪些字段"的规则收口在这一个方法里，
     * Service/Controller 直接调用，不允许在外面手动 new 散着 set，
     * 避免哪天加字段时漏改或不小心带出 passwordHash。</p>
     *
     * @param user 用户实体，调用方需保证非 null
     * @return 用户出参
     */
    public static UserVO from(UserAccount user) {
        return new UserVO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus(),
                user.getCreateTime(),
                user.getUpdateTime()
        );
    }
}