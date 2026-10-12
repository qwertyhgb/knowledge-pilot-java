package com.knowledgepilot.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.knowledgepilot.dto.UserCreateRequest;
import com.knowledgepilot.entity.UserAccount;
import com.knowledgepilot.vo.UserVO;

/**
 * 用户账号业务服务。
 *
 * <p>继承 {@link IService} 获得 MyBatis-Plus 的通用 CRUD 能力，
 * 自定义业务方法在本接口声明、{@code UserAccountServiceImpl} 中实现。</p>
 */
public interface UserAccountService extends IService<UserAccount> {

    /**
     * 创建用户（注册）。
     *
     * <p>业务规则：</p>
     * <ul>
     *   <li>用户名去首尾空格后必须唯一；邮箱去空格、转小写后必须唯一（邮箱可空）</li>
     *   <li>明文口令经 BCrypt 散列后才允许落库，任何场景不回显口令</li>
     *   <li>新账号默认启用（status=1）</li>
     * </ul>
     *
     * <p>并发说明：方法内先 SELECT 查重以提供精确错误提示，
     * 库表唯一索引（uk_username/uk_email）是最终防线，
     * 并发竞态导致的重复键异常会被转换为业务异常。</p>
     *
     * @param request 注册请求（已通过 JSR-380 校验）
     * @return 新建用户的脱敏信息
     * @throws com.knowledgepilot.common.BizException 用户名或邮箱已存在时抛出（409）
     */
    UserVO createUser(UserCreateRequest request);
}
