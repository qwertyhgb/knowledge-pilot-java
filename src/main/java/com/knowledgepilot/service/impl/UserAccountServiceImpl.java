package com.knowledgepilot.service.impl;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.knowledgepilot.common.BizException;
import com.knowledgepilot.dto.UserCreateRequest;
import com.knowledgepilot.entity.UserAccount;
import com.knowledgepilot.mapper.UserAccountMapper;
import com.knowledgepilot.service.UserAccountService;

import lombok.extern.slf4j.Slf4j;

@Slf4j 
@Service 
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService{

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public UserAccount createUser(UserCreateRequest request) {
        LambdaQueryWrapper<UserAccount> usernameWrapper = new LambdaQueryWrapper<>();
        usernameWrapper.eq(UserAccount::getUsername, request.getUsername());
        if (exists(usernameWrapper)) {
            throw new BizException("用户名已存在");
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            LambdaQueryWrapper<UserAccount> emailWrapper = new LambdaQueryWrapper<>();
            emailWrapper.eq(UserAccount::getEmail, request.getEmail());
            if (exists(emailWrapper)) {
                throw new BizException("邮箱已存在");
            }
        }

        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(request.getUsername());
        userAccount.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userAccount.setEmail(request.getEmail());
        userAccount.setStatus(1);

        save(userAccount);
        log.info("用户注册成功: id={}, username={}", userAccount.getId(), userAccount.getUsername());

        userAccount.setPasswordHash(null);
        return userAccount;
    }
    
    
}