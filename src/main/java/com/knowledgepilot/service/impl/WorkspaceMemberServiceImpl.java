package com.knowledgepilot.service.impl;

import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.knowledgepilot.auth.UserContext;
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
public class WorkspaceMemberServiceImpl extends ServiceImpl<WorkspaceMemberMapper, WorkspaceMember>
        implements WorkspaceMemberService {

    private static final String ROLE_OWNER = "OWNER";

    private static final String ROLE_MEMBER = "MEMBER";

    private static final int STATUS_ENABLED = 1;

    private final UserAccountMapper userAccountMapper;
    private final WorkspaceMapper workspaceMapper;

    public WorkspaceMemberServiceImpl(UserAccountMapper userAccountMapper,
            WorkspaceMapper workspaceMapper) {
        this.userAccountMapper = userAccountMapper;
        this.workspaceMapper = workspaceMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceMemberVO addWorkspaceMember(Long workspaceId, WorkspaceMemberAddRequest request) {

        Long targetUserId = request.getUserId();
        Long operatorId = UserContext.requireUserId();

        // 1. 空间必须存在（selectById 自动追加 deleted=0，逻辑删除的不算）
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw BizException.notFound("工作空间不存在");
        }

        // 2. 权限：操作者必须是该空间 OWNER（复用 requireOwner，含账号可用性校验）
        //    鉴权必须排在归档校验之前：否则非成员能靠 403 的文案差异推断空间是否已归档
        requireOwner(workspaceId, operatorId);

        // 3. 归档空间“看得见、改不了”：与移出成员保持同一口径，
        //    归档状态下不允许再改动成员关系（否则会出现“进成员被堵、出人却放行”的不一致）
        if (workspace.getStatus() != null && workspace.getStatus() != STATUS_ENABLED) {
            throw BizException.forbidden("工作空间已归档停用，不能添加成员");
        }

        // 4. 被添加用户必须存在且账号启用（放在鉴权之后，避免向无权限者泄露用户是否存在）
        UserAccount targetUser = userAccountMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw BizException.notFound("被添加的用户不存在");
        }
        if (targetUser.getStatus() != null && targetUser.getStatus() == 0) {
            throw BizException.forbidden("该用户的账号被禁用");
        }

        // 5. 角色规范化与单一所有者防线：添加成员仅支持 MEMBER，防止空间出现多个 OWNER
        String role = StringUtils.hasText(request.getRole()) ? request.getRole().trim().toUpperCase(Locale.ROOT)
                : ROLE_MEMBER;
        if (ROLE_OWNER.equals(role)) {
            throw BizException.badRequest("空间所有者具有唯一性，不能通过添加成员指定 OWNER");
        }
        if (!ROLE_MEMBER.equals(role)) {
            throw BizException.badRequest("角色只能是 MEMBER");
        }

        // 6. 前置查重：给调用方精确提示；并发竞态由步骤 7 的 uk_ws_user 唯一索引兜底
        if (lambdaQuery().eq(WorkspaceMember::getWorkspaceId, workspaceId).eq(WorkspaceMember::getUserId, targetUserId)
                .exists()) {
            throw BizException.conflict("该用户已经是空间成员");
        }

        // 7. 落库：uk_ws_user(workspace_id, user_id) 联合唯一索引是最终防线
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setRole(role);
        member.setUserId(targetUserId);
        try {
            save(member);
        } catch (DuplicateKeyException exception) {
            throw BizException.conflict("该用户已经是空间成员", exception);
        }

        log.info("空间成员添加成功: workspaceId={}, userId={}, role={}, operatorId={}",
                workspaceId, targetUserId, role, operatorId);

        return WorkspaceMemberVO.from(member);
    }

    @Override
    public void removeWorkspaceMember(Long workspaceId, Long userId) {

        // 1. 操作者身份只能来自登录上下文，禁止请求体传入 operatorId
        Long operatorId = UserContext.requireUserId();

        // 2. 空间必须存在（selectById 自动追加 deleted=0，逻辑删除的不算）
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw BizException.notFound("工作空间不存在");
        }

        // 3. 权限：操作者必须是该空间 OWNER（复用 requireOwner，含账号可用性校验）
        //    鉴权必须排在归档校验之前：否则非成员能靠 403 的文案差异推断空间是否已归档
        requireOwner(workspaceId, operatorId);

        // 4. 归档空间“看得见、改不了”：与添加成员保持同一口径，
        //    归档状态下不允许再改动成员关系（否则会出现“进成员被堵、出人却放行”的不一致）
        if (workspace.getStatus() != null && workspace.getStatus() != STATUS_ENABLED) {
            throw BizException.forbidden("工作空间已归档停用，不能移出成员");
        }

        // 5. 目标成员必须存在（放在鉴权之后，避免向无权限者泄露成员是否存在）
        WorkspaceMember targetWorkspaceMember = lambdaQuery().eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getUserId, userId).one();
        if (targetWorkspaceMember == null) {
            throw BizException.notFound("工作空间成员不存在");
        }

        // 6. 不能移出 OWNER 本人：空间所有者具有唯一性，需先转让所有权或删除空间
        if (ROLE_OWNER.equals(targetWorkspaceMember.getRole())) {
            throw BizException.badRequest("不能移除空间 OWNER，请先转让所有权或删除空间");
        }

        // 7. 物理删除：workspace_member 无 deleted 列，退出即删行，
        //    重新加入时不会与 uk_ws_user 唯一索引打架
        boolean removed = removeById(targetWorkspaceMember.getId());
        if (!removed) {
            // 走到这里说明成员在第 5 步查询之后被并发移除
            throw BizException.notFound("该成员已被移出空间");
        }

        log.info("工作空间成员移除成功: workspaceId={}, userId={}, operatorId={}", workspaceId, userId, operatorId);
    }

    /**
     * 校验操作者是否为该空间的 OWNER。
     *
     * <p>集中封装“空间管理权限”这道闸门，供添加成员、移出成员复用：
     * 操作者必须是该空间成员且角色为 OWNER；其账号也必须未被禁用
     * （纵深防御，不依赖“调用方一定先鉴权”的假设）。</p>
     *
     * <p>调用方必须把本方法放在归档校验<b>之前</b>：两个分支都是 403，
     * 若先判归档，非成员就能靠 403 的文案差异推断出该空间是否已归档。</p>
     *
     * @param workspaceId 空间ID
     * @param operatorId  操作者用户ID（取自 {@code UserContext}，不接受外部传入）
     * @throws com.knowledgepilot.common.BizException 不是空间成员或不是 OWNER（403）、操作者账号被禁用（403）
     */
    private void requireOwner(Long workspaceId, Long operatorId) {
        WorkspaceMember workspaceMember = lambdaQuery().eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getUserId, operatorId).one();
        if (workspaceMember == null) {
            throw BizException.forbidden("操作者不是该空间成员，无权操作");
        }
        if (!ROLE_OWNER.equals(workspaceMember.getRole())) {
            throw BizException.forbidden("只有空间 OWNER 才可以操作成员");
        }

        UserAccount operatorUser = userAccountMapper.selectById(operatorId);
        if (operatorUser != null && operatorUser.getStatus() != null && operatorUser.getStatus() == 0) {
            throw BizException.forbidden("操作者账号已被禁用");
        }
    }
}
