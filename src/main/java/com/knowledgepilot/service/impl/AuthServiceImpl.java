package com.knowledgepilot.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgepilot.auth.LoginSession;
import com.knowledgepilot.auth.LoginUser;
import com.knowledgepilot.auth.SessionStore;
import com.knowledgepilot.auth.TokenGenerator;
import com.knowledgepilot.common.BizException;
import com.knowledgepilot.dto.LoginRequest;
import com.knowledgepilot.entity.UserAccount;
import com.knowledgepilot.mapper.UserAccountMapper;
import com.knowledgepilot.service.AuthService;
import com.knowledgepilot.vo.LoginVO;

import lombok.extern.slf4j.Slf4j;

/**
 * 登录认证业务服务实现。
 *
 * <p>
 * 不继承 MyBatis-Plus 的 {@code ServiceImpl}：认证只需要按用户名查一次用户，
 * 用不到通用 CRUD；依赖显式声明为 4 个协作者，职责更清楚。
 * </p>
 *
 * <p>
 * 不声明 {@code @Transactional}：登录是“读用户 + 写内存会话”，登出是纯内存删除，
 * 没有多表写入，事务在这里没有意义。
 * </p>
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 会话有效期 2 小时；等出现按环境/用户调整的需求再外置到配置 */
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    /** 登录失败统一文案：用户不存在与密码错误必须一致，防止用户名枚举 */
    private static final String LOGIN_FAILED_MESSAGE = "用户名或密码错误";

    /** 账号启用状态值：对应 user_account.status = 1 */
    private static final int STATUS_ENABLED = 1;

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final SessionStore sessionStore;

    /**
     * 假散列：用户不存在时用它跑一次 BCrypt，使“用户不存在”与“密码错误”耗时接近。
     * 内容本身无意义，每次启动生成一次即可。
     */
    private final String dummyHash;

    public AuthServiceImpl(UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder,
            TokenGenerator tokenGenerator,
            SessionStore sessionStore) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.sessionStore = sessionStore;
        this.dummyHash = passwordEncoder.encode("kp-dummy-" + UUID.randomUUID());
    }

    @Override
    public LoginVO login(LoginRequest request) {
        // 用户名 trim 与注册落库口径对齐；密码必须原样比对，空格属于密码的一部分。
        // 长度只由 LoginRequest 的 @Size(max = 64) 按字符数约束，Service 不重复校验。
        // 已知边界：多字节字符（如 64 个汉字）可能超过 BCrypt 的 72 字节处理上限，
        // 当前不特殊处理，等真实问题出现再统一注册与登录的口径。
        String username = request.getUsername().trim();
        String rawPassword = request.getPassword();

        UserAccount user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username));
        if (user == null) {
            // 用户不存在时也跑一次 BCrypt，避免用响应时间差枚举用户名；返回值无意义，勿删
            passwordEncoder.matches(rawPassword, dummyHash);
            throw BizException.unauthorized(LOGIN_FAILED_MESSAGE);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw BizException.unauthorized(LOGIN_FAILED_MESSAGE);
        }

        // 禁用必须放在密码校验之后判断，否则会泄露“该用户名存在且被禁用”
        if (user.getStatus() != null && user.getStatus() != STATUS_ENABLED) {
            throw BizException.forbidden("账号已被禁用");
        }

        Instant now = Instant.now();
        LoginSession session = new LoginSession(tokenGenerator.generate(), user.getId(), now.plus(SESSION_TTL));
        sessionStore.save(session);

        log.info("登录成功: userId={}, username={}, expiresAt={}",
                user.getId(), user.getUsername(), session.expiresAt());

        return LoginVO.from(session, new LoginUser(user.getId(), user.getUsername()));
    }

    @Override
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        sessionStore.remove(token);
        // remove 无法区分 token 是否真实存在（幂等语义），这里只记录“请求已处理”，不声称“成功”
        log.info("登出请求已处理");
    }

}
