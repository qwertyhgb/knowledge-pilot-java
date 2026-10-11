package com.knowledgepilot.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.knowledgepilot.dto.UserCreateRequest;
import com.knowledgepilot.entity.UserAccount;

public interface UserAccountService extends IService<UserAccount> {

    UserAccount createUser(UserCreateRequest request);
}
