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
 * <p>继承 {@link ServiceImpl} 复用 MyBatis-Plus 通用 CRUD（如 {@code save}、{@code lambdaQuery}），
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

        // 2. 前置查重：提供精确的错误提示（区分用户名/邮箱）；并发竞态由步骤 4 的唯一索引兜底
        if (lambdaQuery().eq(UserAccount::getUsername, username).exists()) {
            throw BizException.conflict("用户名已存在");
        }
        if (email != null && lambdaQuery().eq(UserAccount::getEmail, email).exists()) {
            throw BizException.conflict("邮箱已存在");
        }

        // 3. 组装实体：明文口令只在此处经 BCrypt 散列一次，散列值写入 password_hash
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(username);
        userAccount.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userAccount.setEmail(email);
        userAccount.setStatus(STATUS_ENABLED);

        // 4. 落库：唯一索引是最终防线，捕获并发下的重复键冲突并保留原因链
        try {
            save(userAccount);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "用户名或邮箱已存在", e);
        }

        log.info("用户注册成功: id={}, username={}", userAccount.getId(), username);
        return UserVO.from(userAccount);
    }
}
