package com.knowledgepilot.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.knowledgepilot.auth.UserContext;
import com.knowledgepilot.common.BizException;
import com.knowledgepilot.dto.WorkspaceCreateRequest;
import com.knowledgepilot.dto.WorkspaceStatusUpdateRequest;
import com.knowledgepilot.dto.WorkspaceUpdateRequest;
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
    public PageResult<WorkspaceVO> listMyWorkspaces(long pageNum, long pageSize) {

        // 查询主体只能来自登录上下文，不接受外部传入 userId，杜绝 IDOR；
        // 拦截器已保证该用户存在且启用，下方校验属纵深防御（不依赖“调用方一定先鉴权”的假设）
        Long userId = UserContext.requireUserId();

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

    @Override
    public WorkspaceVO updateWorkspace(Long workspaceId, WorkspaceUpdateRequest request) {

        // 1. 操作者身份只能来自登录上下文，禁止请求体传入 operatorId
        Long operatorId = UserContext.requireUserId();

        // 2. 用路径中的空间ID查询；getById 会自动追加 deleted=0
        Workspace workspace = getById(workspaceId);
        if (workspace == null) {
            throw BizException.notFound("工作空间不存在");
        }
        // 3. 归档空间“看得见、改不了”，与添加成员的规则保持一致
        if (workspace.getStatus() != null && workspace.getStatus() != STATUS_ENABLED) {
            throw BizException.forbidden("工作空间已归档停用，不能修改");
        }

        // 4. 权限：操作者必须是该空间 OWNER（含账号禁用纵深防御，收口在 requireOwner）
        requireOwner(workspaceId, operatorId);

        // 5. 字段归一：name 必填并 trim；description 空白串归一为 null（表示清空）
        String name = request.getName().trim();
        String description = StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null;

        // 6. 显式 set 而不是 updateById：后者会忽略 null 字段，无法清空 description。
        // 状态只能作为 where 条件（eq），绝不能 set：set(status=1) 会把归档空间“复活”。
        boolean updated = lambdaUpdate().eq(Workspace::getId, workspaceId)
                .eq(Workspace::getStatus, STATUS_ENABLED)
                .set(Workspace::getName, name)
                .set(Workspace::getDescription, description)
                .update();
        if (!updated) {
            throw BizException.notFound("工作空间不存在或已被删除");
        }

        log.info("工作空间更新成功: workspaceId={}, name={}, operatorId={}", workspaceId, name, operatorId);

        // 7. 用空间ID重新查询，拿到最新的 updateTime；绝不能用 operatorId
        Workspace latest = getById(workspaceId);
        if (latest == null) {
            throw BizException.notFound("工作空间不存在或已被删除");
        }

        return WorkspaceVO.from(latest);
    }

    @Override
    public WorkspaceVO updateWorkspaceStatus(Long workspaceId, WorkspaceStatusUpdateRequest request) {

        // 1. 操作者身份只能来自登录上下文，禁止请求体传入 operatorId
        Long operatorId = UserContext.requireUserId();
        // 2. 目标状态：DTO 已用 @Min/@Max 白名单锁死 0/1；
        // Integer 拆箱为 int，依赖 DTO 的 @NotNull + Controller 的 @Valid 兜底
        int targetStatus = request.getStatus();

        // 3. 空间必须存在（getById 自动追加 deleted=0，已逻辑删除的不算）。
        // 这里刻意不像 updateWorkspace 那样挡“已归档”——恢复（0→1）必须能在归档空间上执行：
        // “看得见、改不了”约束的是内容修改，状态机自身的归档/恢复不受此约束
        Workspace workspace = getById(workspaceId);
        if (workspace == null) {
            throw BizException.notFound("工作空间不存在");
        }

        // 4. 权限：操作者必须是该空间 OWNER（含账号禁用纵深防御，收口在 requireOwner）
        requireOwner(workspaceId, operatorId);

        // 5. 幂等短路：当前状态已是目标值，不写库直接成功返回（归档两次 / 恢复两次都返回成功）
        int currentStatus = workspace.getStatus() == null ? STATUS_ENABLED : workspace.getStatus();

        if (currentStatus == targetStatus) {
            log.info("工作空间状态未变化，按幂等成功返回: workspaceId={}, status={}, operatorId={}",
                    workspaceId, currentStatus, operatorId);
            return WorkspaceVO.from(workspace);
        }

        // 6. CAS（Compare-And-Swap）乐观并发：WHERE 里带上“期望的旧状态”，
        // 状态若已被别的请求改过，这条 UPDATE 影响行数为 0——两个并发请求天然只有一个成功，无需加锁
        boolean updated = lambdaUpdate().eq(Workspace::getId, workspaceId).eq(Workspace::getStatus, currentStatus)
                .set(Workspace::getStatus, targetStatus).update();
        if (!updated) {
            // 7. CAS 未打中，重新读库分辨三种情况，不直接报错：
            // a. 已被并发逻辑删除 → 404
            // b. 已被并发置为目标值 → 结果已达成，按幂等成功返回
            // c. 状态被改成其他值 → 真冲突，409 让客户端刷新后重试
            Workspace latest = getById(workspaceId);
            if (latest == null) {
                throw BizException.notFound("工作空间不存在或已被删除");
            }
            if (latest.getStatus() != null && latest.getStatus() == targetStatus) {
                log.info("工作空间状态已被并发请求置为目标值，按幂等成功返回: workspaceId={}, status={}",
                        workspaceId, targetStatus);
                return WorkspaceVO.from(latest);
            }
            throw BizException.conflict("空间状态已被其他操作修改，请刷新后重试");
        }

        log.info("工作空间状态变更成功: workspaceId={}, {} -> {}, operatorId={}",
                workspaceId, currentStatus, targetStatus, operatorId);

        // 8. 重新查询拿最新状态与 updateTime，不用 CAS 前读到的旧对象组装 VO
        Workspace latestAfterUpdate = getById(workspaceId);
        if (latestAfterUpdate == null) {
            throw BizException.notFound("工作空间不存在或已被删除");
        }

        return WorkspaceVO.from(latestAfterUpdate);
    }

    /**
     * 校验操作者是该空间的 OWNER，并顺带做“账号是否被禁用”的纵深防御。
     *
     * <p>鉴权拦截器已保证账号可用，这里防的是“鉴权通过后、执行前被禁用”的极端窗口
     * （拦截器与业务之间并非原子）。两个更新接口共用，避免权限口径漂移。</p>
     *
     * @param workspaceId 空间ID
     * @param operatorId  操作者用户ID（来自 UserContext）
     * @throws com.knowledgepilot.common.BizException 非成员或非 OWNER（403）、账号被禁用（403）
     */
    private void requireOwner(Long workspaceId, Long operatorId) {
        // 不能用 lambdaQuery()——它绑定的是本类实体 Workspace，查成员表必须走 workspaceMemberMapper
        WorkspaceMember operator = workspaceMemberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMember>()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId).eq(WorkspaceMember::getUserId, operatorId));
        if (operator == null) {
            throw BizException.forbidden("操作者不是该空间成员，无权修改");
        }
        if (!ROLE_OWNER.equals(operator.getRole())) {
            throw BizException.forbidden("只有空间 OWNER 才可以操作该空间");
        }
        // 纵深防御：鉴权拦截器已校验账号可用，这里防“鉴权后被禁用”的极端情况
        UserAccount operatorUser = userAccountMapper.selectById(operatorId);
        if (operatorUser != null && operatorUser.getStatus() != null && operatorUser.getStatus() == 0) {
            throw BizException.forbidden("操作者账号已被禁用");
        }
    }

}
