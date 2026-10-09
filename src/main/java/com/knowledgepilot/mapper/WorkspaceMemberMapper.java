package com.knowledgepilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgepilot.entity.WorkspaceMember;

/**
 * 工作空间成员 Mapper。
 *
 * <p>V0.1 只继承 {@link BaseMapper}，不写 XML、不写自定义 SQL。
 * 注意：实体没有 deleted 字段（也不加 @TableLogic），所以 deleteById 是物理 DELETE，
 * 这正是成员关系“退出=真删、可干净重加”的设计约定。
 */
public interface WorkspaceMemberMapper extends BaseMapper<WorkspaceMember> {
}
