package com.knowledgepilot.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.knowledgepilot.common.Result;
import com.knowledgepilot.dto.LoginRequest;
import com.knowledgepilot.service.AuthService;
import com.knowledgepilot.vo.LoginVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 登录认证接口。
 *
 * <p>
 * 只做 HTTP 协议转换：参数校验、请求头解析与统一响应；
 * 凭证校验与会话管理全部在 {@link AuthService}。
 * </p>
 *
 * <p>
 * 登录与登出均在鉴权白名单内（见 {@code WebMvcConfig}），
 * 因此本类不能依赖 {@code UserContext} 获取当前用户。
 * </p>
 */
@Tag(name = "登录认证", description = "登录与登出接口")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String AUTH_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录。
     *
     * <p>处理链路：</p>
     * <ol>
     *   <li>{@code @Valid} 触发 {@link LoginRequest} 上的 JSR-380 校验（非空、长度），
     *       失败由 {@code GlobalExceptionHandler} 统一转成 400；</li>
     *   <li>Service 层完成用户名规范化、口令比对与会话创建；</li>
     *   <li>成功返回令牌、过期时间与最小用户信息（不含口令散列）。</li>
     * </ol>
     *
     * <p>失败口径：用户名不存在与口令错误统一返回 401 且文案一致，防止用户名枚举；
     * 账号被禁用返回 403。</p>
     *
     * @param request 登录请求体
     * @return 登录成功后的令牌与用户信息
     */
    @Operation(summary = "登录", description = "校验用户名与密码，成功后返回会话令牌")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 登出。
     *
     * <p>处理链路：</p>
     * <ol>
     *   <li>从 {@code Authorization} 头取令牌，兼容 {@code Bearer <token>} 与裸 token；</li>
     *   <li>头缺失或为空时传 null 给 Service，由 Service 直接返回（登出幂等）；</li>
     *   <li>删除服务端会话后返回成功；客户端清除本地 token 由前端负责。</li>
     * </ol>
     *
     * <p>本接口在鉴权白名单内，因此不依赖 {@code UserContext}；
     * 令牌无效或已过期同样返回成功，避免用户会话过期后无法登出。</p>
     *
     * @param authorization 原始 Authorization 头，允许缺失
     * @return 空响应体，仅表示请求已处理
     */
    @Operation(summary = "登出", description = "使当前令牌失效；token 缺失或已过期也返回成功（幂等）")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = AUTH_HEADER, required = false) String authorization) {
        authService.logout(resolveToken(authorization));
        return Result.success();
    }

    private static String resolveToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length()).trim()
                : authorization.trim();
    }
}
