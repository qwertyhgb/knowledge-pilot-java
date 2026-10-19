package com.knowledgepilot.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

import com.knowledgepilot.auth.LoginSession;
import com.knowledgepilot.auth.LoginUser;

import lombok.Data;

/**
 * 登录成功出参（View Object）。
 *
 * <p>字段即“客户端继续通信所需的最小集合”：token 用于后续请求的
 * Authorization 头，expiresAt 仅供前端做过期提示，真正的过期校验
 * 在服务端 {@code LoginSession#isExpired}，不得信任客户端看到的时间。</p>
 *
 * <p>脱敏红线：passwordHash、deleted 严禁在此出现；
 * 用户信息只带 id/username（来自 LoginUser 身份快照），不带实体全量字段。</p>
 */
@Data
@Schema(description = "登录成功响应")
public class LoginVO {

    @Schema(description = "登录令牌，后续请求放 Authorization: Bearer <token> 头", example = "q7f...43chars")
    private String token;

    @Schema(description = "会话过期时间（UTC），仅作前端提示，以服务端校验为准", example = "2026-10-20T12:00:00Z")
    private Instant expiresAt;

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "登录名", example = "zhangsan")
    private String username;

    /**
     * 会话 + 身份 → 出参的转换工厂，命名与 {@link UserVO#from} 保持一致。
     *
     * @param session 登录会话，调用方需保证非 null
     * @param user    当前登录用户，调用方需保证非 null
     * @return 登录出参
     */
    public static LoginVO from(LoginSession session, LoginUser user) {
        LoginVO vo = new LoginVO();
        vo.setToken(session.token());
        vo.setExpiresAt(session.expiresAt());
        vo.setUserId(user.id());
        vo.setUsername(user.username());
        return vo;
    }
}
