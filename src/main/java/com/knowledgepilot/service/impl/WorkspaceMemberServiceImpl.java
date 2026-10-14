package com.knowledgepilot.service.impl;

import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.knowledgepilot.common.BizException;
import com.knowledgepilot.dto.WorkspaceMemberAddRequest;
import com.knowledgepilot.entity.UserAccount;
import com.knowledgepilot.entity.Workspace;
import com.knowledgepilot.entity.WorkspaceMember;
import com.knowledgepilot.mapper.UserAccountMapper;
import com.knowledgepilot.mapper.WorkspaceMapper;
import com.knowledgepilot.mapper.WorkspaceMemberMapper;
import com.knowledgepilot.service.WorkspaceMemberService;
import com.knowledgepilot.vo.WorkspaceMemberVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作空间成员业务服务实现。
 *
 * <p>
 * 继承 {@link ServiceImpl} 复用 MyBatis-Plus 对 workspace_member 表的通用 CRUD。
 * 添加成员需联动校验 workspace 与 user_account 两张表，统一受事务管控。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceMemberServiceImpl extends ServiceImpl<WorkspaceMemberMapper, WorkspaceMember>
        implements WorkspaceMemberService {

    private static final String ROLE_OWNER = "OWNER";

    private static final String ROLE_MEMBER = "MEMBER";

    private static final int STATUS_ENABLED = 1;

    private final UserAccountMapper userAccountMapper;
    private final WorkspaceMapper workspaceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceMemberVO addWorkspaceMember(Long workspaceId, WorkspaceMemberAddRequest request) {

        Long targetUserId = request.getUserId();
        Long operatorId = request.getOperatorId();

        // 1. 空间必须存在且未归档停用（selectById 自动追加 deleted=0，逻辑删除的不算）
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw BizException.notFound("工作空间不存在");
        }
        if (workspace.getStatus() != null && workspace.getStatus() != STATUS_ENABLED) {
            throw BizException.forbidden("工作空间已归档停用，不能添加成员");
        }

        // 2. 被添加用户必须存在且账号启用
        UserAccount targetUser = userAccountMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw BizException.notFound("被添加的用户不存在");
        }
        if (targetUser.getStatus() != null && targetUser.getStatus() == 0) {
            throw BizException.forbidden("该用户的账号被禁用");
        }

        // 3. 权限校验：操作者必须是该空间的 OWNER（无登录态前临时用 operatorId，
        // 接入认证后改为从登录上下文取当前用户，见 WorkspaceMemberAddRequest 的 TODO）
        WorkspaceMember operator = lambdaQuery().eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getUserId, operatorId).one();
        if (operator == null) {
            throw BizException.forbidden("操作者不是该空间成员，无权添加成员");
        }
        if (!ROLE_OWNER.equals(operator.getRole())) {
            throw BizException.forbidden("只有空间 OWNER 才可以添加成员");
        }
        UserAccount operatorUser = userAccountMapper.selectById(operatorId);
        if (operatorUser != null && operatorUser.getStatus() != null && operatorUser.getStatus() == 0) {
            throw BizException.forbidden("操作者账号已被禁用");
        }

        // 4. 角色规范化与单一所有者防线：添加成员仅支持 MEMBER，防止空间出现多个 OWNER
        String role = StringUtils.hasText(request.getRole()) ? request.getRole().trim().toUpperCase(Locale.ROOT)
                : ROLE_MEMBER;
        if (ROLE_OWNER.equals(role)) {
            throw BizException.badRequest("空间所有者具有唯一性，不能通过添加成员指定 OWNER");
        }
        if (!ROLE_MEMBER.equals(role)) {
            throw BizException.badRequest("角色只能是 MEMBER");
        }

        // 5. 前置查重：给调用方精确提示；并发竞态由步骤 6 的 uk_ws_user 唯一索引兜底
        if (lambdaQuery().eq(WorkspaceMember::getWorkspaceId, workspaceId).eq(WorkspaceMember::getUserId, targetUserId)
                .exists()) {
            throw BizException.conflict("该用户已经是空间成员");
        }

        // 6. 落库：uk_ws_user(workspace_id, user_id) 联合唯一索引是最终防线
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setRole(role);
        member.setUserId(targetUserId);
        try {
            save(member);
        } catch (DuplicateKeyException exception) {
            throw new BizException(409, "该用户已是空间成员", exception);
        }

        log.info("空间成员添加成功: workspaceId={}, userId={}, role={}, operatorId={}",
                workspaceId, targetUserId, role, operatorId);

        return WorkspaceMemberVO.from(member);
    }

}
