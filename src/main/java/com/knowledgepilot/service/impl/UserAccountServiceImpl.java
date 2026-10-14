package com.knowledgepilot.service.impl;

import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.knowledgepilot.common.BizException;
import com.knowledgepilot.dto.UserCreateRequest;
import com.knowledgepilot.entity.UserAccount;
import com.knowledgepilot.mapper.UserAccountMapper;
import com.knowledgepilot.service.UserAccountService;
import com.knowledgepilot.vo.UserVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户账号业务服务实现。
 *
 * <p>继承 {@link ServiceImpl} 复用 MyBatis-Plus 通用 CRUD（如 {@code save}、{@code lambdaQuery}，
 * 以及类型为 {@link UserAccountMapper} 的 {@code baseMapper}），
 * 口令编码器依赖 {@link PasswordEncoder} 接口而非具体实现，便于将来替换散列算法。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl
        extends ServiceImpl<UserAccountMapper, UserAccount>
        implements UserAccountService {

    /** 新用户默认状态：启用（对应 user_account.status=1） */
    private static final int STATUS_ENABLED = 1;

    /**
     * 墓碑冲突提示（用户名）。
     *
     * <p>逻辑删除的账号在库里只是 {@code deleted = 1}，但唯一索引 {@code uk_username}
     * 是物理约束，仍被这条"墓碑行"占着。告诉调用方真实原因和下一步动作，
     * 比笼统的"用户名已存在"更有用，也避免排查时误以为存在活跃账号。</p>
     */
    private static final String TOMBSTONE_USERNAME_MESSAGE =
            "用户名已被一个已注销的账号占用，无法重新注册，请联系管理员恢复或清理该账号";

    /** 墓碑冲突提示（邮箱），语义同 {@link #TOMBSTONE_USERNAME_MESSAGE} */
    private static final String TOMBSTONE_EMAIL_MESSAGE =
            "邮箱已被一个已注销的账号占用，无法重新注册，请联系管理员恢复或清理该账号";

    /** 口令编码器，Bean 定义在 SecurityBeanConfig，实际实现为 BCryptPasswordEncoder */
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(UserCreateRequest request) {
        // 1. 入参规范化：用户名去首尾空格；邮箱去空格 + 转小写（邮箱大小写不敏感）。
        //    规范化后的值必须贯穿查重与落库全程，不能回头使用 request 原始值。
        String username = request.getUsername().trim();
        String email = StringUtils.hasText(request.getEmail())
                ? request.getEmail().trim().toLowerCase(Locale.ROOT)
                : null;

        // 2. 活跃账号前置查重：提供精确的错误提示（区分用户名/邮箱）
        if (lambdaQuery().eq(UserAccount::getUsername, username).exists()) {
            throw BizException.conflict("用户名已存在");
        }
        if (email != null && lambdaQuery().eq(UserAccount::getEmail, email).exists()) {
            throw BizException.conflict("邮箱已存在");
        }

        // 3. 墓碑查重：逻辑删除的账号仍物理占用 uk_username / uk_email，但上面的 lambdaQuery
        //    会被 MP 自动追加 deleted = 0，查不到它。若不在这里拦截，INSERT 会直接撞唯一索引，
        //    用户只会收到"用户名或邮箱已存在"这种指错方向的提示。
        if (baseMapper.selectTombstoneIdByUsername(username) != null) {
            throw BizException.conflict(TOMBSTONE_USERNAME_MESSAGE);
        }
        if (email != null && baseMapper.selectTombstoneIdByEmail(email) != null) {
            throw BizException.conflict(TOMBSTONE_EMAIL_MESSAGE);
        }

        // 4. 组装实体：明文口令只在此处经 BCrypt 散列一次，散列值写入 password_hash
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(username);
        userAccount.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userAccount.setEmail(email);
        userAccount.setStatus(STATUS_ENABLED);

        // 5. 落库：唯一索引是最终防线。能走到 catch 说明前置查重通过后仍撞了唯一键，
        //    典型场景是同名/同邮箱的并发注册；保留原因链方便日后排查。
        try {
            save(userAccount);
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("用户名或邮箱已被占用，请更换后重试", e);
        }

        log.info("用户注册成功: id={}, username={}", userAccount.getId(), username);
        return UserVO.from(userAccount);
    }
}
