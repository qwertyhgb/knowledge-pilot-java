package com.knowledgepilot.vo;

import com.knowledgepilot.entity.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息出参（View Object）。
 *
 * <p>采用普通 POJO 类结合 Lombok 声明，与项目整体风格保持一致。
 * 只承载可对前端暴露的字段，自动完成脱敏：
 * 内部字段 {@code passwordHash}（口令散列）与 {@code deleted}（逻辑删除标记）严禁在此暴露。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户信息")
public class UserVO {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "登录名", example = "zhangsan")
    private String username;

    @Schema(description = "邮箱，可空", example = "zhangsan@example.com", nullable = true)
    private String email;

    @Schema(description = "状态：0-禁用，1-启用", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "最后修改时间")
    private LocalDateTime updateTime;

    /**
     * 实体 → VO 的转换工厂。
     *
     * <p>收口字段摘取与脱敏逻辑，外部统一通过 {@code UserVO.from(user)} 调用，
     * 避免在 Service 层手动 new 和 set 导致字段遗漏或泄露。</p>
     *
     * @param user 用户实体，调用方需保证非 null
     * @return 用户展示出参
     */
    public static UserVO from(UserAccount user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}