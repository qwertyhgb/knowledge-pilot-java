package com.knowledgepilot.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.knowledgepilot.auth.UserContext;
import com.knowledgepilot.common.BizException;
import com.knowledgepilot.dto.WorkspaceCreateRequest;
import com.knowledgepilot.entity.UserAccount;
import com.knowledgepilot.entity.Workspace;
import com.knowledgepilot.entity.WorkspaceMember;
import com.knowledgepilot.mapper.UserAccountMapper;
import com.knowledgepilot.mapper.WorkspaceMapper;
import com.knowledgepilot.mapper.WorkspaceMemberMapper;
import com.knowledgepilot.service.WorkspaceService;
import com.knowledgepilot.vo.PageResult;
import com.knowledgepilot.vo.WorkspaceVO;

import lombok.extern.slf4j.Slf4j;

/**
 * 工作空间业务服务实现。
 *
 * <p>
 * 继承 {@link ServiceImpl} 复用 MyBatis-Plus 对 Workspace 表的通用 CRUD。
 * 创建空间涉及 workspace 与 workspace_member 两张表的联动写入，统一受事务管控。
 * </p>
 */
@Slf4j
@Service
public class WorkspaceServiceImpl extends ServiceImpl<WorkspaceMapper, Workspace> implements WorkspaceService {

    /** 默认空间状态：1-正常（对应 workspace.status=1） */
    private static final int STATUS_ENABLED = 1;

    /** 空间拥有者角色（对应 workspace_member.role='OWNER'） */
    private static final String ROLE_OWNER = "OWNER";

    private final UserAccountMapper userAccountMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;

    public WorkspaceServiceImpl(UserAccountMapper userAccountMapper,
            WorkspaceMemberMapper workspaceMemberMapper) {
        this.userAccountMapper = userAccountMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceVO createWorkspace(WorkspaceCreateRequest request) {

        String name = request.getName().trim();
        String description = StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null;

        Long creatorId = UserContext.requireUserId();

        UserAccount creator = userAccountMapper.selectById(creatorId);
        if (creator == null) {
            throw BizException.notFound("创建者用户不存在");
        }
        if (creator.getStatus() != null && creator.getStatus() == 0) {
            throw BizException.forbidden("创建者账号已被禁用");
        }

        Workspace workspace = new Workspace();
        workspace.setName(name);
        workspace.setDescription(description);
        workspace.setCreatorId(creatorId);
        workspace.setStatus(STATUS_ENABLED);

        save(workspace);

        WorkspaceMember workspaceMember = new WorkspaceMember();
        workspaceMember.setWorkspaceId(workspace.getId());
        workspaceMember.setUserId(creatorId);
        workspaceMember.setRole(ROLE_OWNER);

        workspaceMemberMapper.insert(workspaceMember);

        log.info("工作空间创建成功: workspaceId={}, name={}, creatorId={}",
                workspace.getId(), name, creatorId);

        return WorkspaceVO.from(workspace);
    }

    @Override
    public PageResult<WorkspaceVO> listByMemberId(Long userId, long pageNum, long pageSize) {

        UserAccount member = userAccountMapper.selectById(userId);
        if (member == null) {
            throw BizException.notFound("用户不存在");
        }
        if (member.getStatus() != null && member.getStatus() == 0) {
            throw BizException.forbidden("用户账号已被禁用");
        }

        long safePageNum = pageNum < 1 ? 1 : pageNum;
        long safePageSize = pageSize < 1 ? 20 : Math.min(pageSize, 200);

        IPage<Workspace> page = baseMapper.selectPageByMember(new Page<>(safePageNum, safePageSize), userId);

        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(WorkspaceVO::from).toList());
    }

}
