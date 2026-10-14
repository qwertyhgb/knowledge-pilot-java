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
     * <p>唯一性校验分三层，越靠后越可靠：</p>
     * <ol>
     *   <li><b>活跃账号前置查重</b>：区分"用户名已存在"/"邮箱已存在"，给前端精确提示；</li>
     *   <li><b>墓碑查重</b>：{@code user_account} 采用逻辑删除，被软删的账号仍物理占用
     *       {@code uk_username}/{@code uk_email}，且普通查询（自动追加 {@code deleted = 0}）
     *       看不到它。这里用 Mapper 的墓碑查询提前拦截，返回"已被已注销的账号占用"的
     *       明确提示，而不是让用户撞上唯一索引后收到笼统的"用户名或邮箱已存在"；</li>
     *   <li><b>唯一索引</b>：并发竞态下的最终防线，重复键异常会被转换成 409 业务异常。</li>
     * </ol>
     *
     * @param request 注册请求（已通过 JSR-380 校验）
     * @return 新建用户的脱敏信息
     * @throws com.knowledgepilot.common.BizException 用户名/邮箱已存在，或已被已注销账号占用时抛出（409）
     */
    UserVO createUser(UserCreateRequest request);
}
