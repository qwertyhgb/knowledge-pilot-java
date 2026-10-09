package com.knowledgepilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgepilot.entity.UserAccount;

/**
 * 用户账号 Mapper。
 *
 * <p>V0.1 只继承 {@link BaseMapper}，不写 XML、不写自定义 SQL。
 * 注意：实体带 @TableLogic，这里生成的 deleteById 是逻辑删除（UPDATE deleted=1）。
 */
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}
