package com.knowledgepilot.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.knowledgepilot.dto.WorkspaceMemberAddRequest;
import com.knowledgepilot.dto.WorkspaceOwnerTransferRequest;
import com.knowledgepilot.entity.WorkspaceMember;
import com.knowledgepilot.vo.WorkspaceMemberVO;

/**
 * 工作空间成员业务服务接口。
 */
public interface WorkspaceMemberService extends IService<WorkspaceMember> {

    /**
     * 向指定空间添加成员。
     *
     * <p>
     * 业务规则：
     * </p>
     * <ul>
     * <li>空间必须存在且未归档停用；</li>
     * <li>被添加用户必须存在且账号启用；</li>
     * <li>操作者必须是该空间的 OWNER（权限校验）；</li>
     * <li>角色只能是 MEMBER，不传默认 MEMBER；空间所有者具有唯一性，不能通过添加成员
     * 指定 OWNER（OWNER 应由空间创建时确定或日后经转让流程变更）；</li>
     * <li>同一用户不能重复加入同一空间（uk_ws_user 联合唯一索引兜底并发）。</li>
     * </ul>
     *
     * @param workspaceId 目标空间ID
     * @param request     添加成员请求
     * @return 新成员展示对象
     * @throws com.knowledgepilot.common.BizException 任一业务规则不满足时抛出（403/404/409）
     */
    WorkspaceMemberVO addWorkspaceMember(Long workspaceId, WorkspaceMemberAddRequest request);

    /**
     * 移出空间成员。
     *
     * <p>
     * 业务规则：
     * </p>
     * <ul>
     * <li>空间必须存在；</li>
     * <li>空间未归档——归档（status=0）状态不允许改动成员关系，与添加成员同一口径；</li>
     * <li>操作者必须是该空间 OWNER（权限校验）；</li>
     * <li>目标成员必须存在，否则 404；</li>
     * <li>不能移出 OWNER 本人——空间所有者具有唯一性，需先转让所有权或删除空间。</li>
     * </ul>
     *
     * @param workspaceId 空间ID
     * @param userId      被移出的用户ID
     * @throws com.knowledgepilot.common.BizException 空间不存在（404）、空间已归档（403）、
     *                                                非 OWNER（403）、目标成员不存在（404）、试图移出
     *                                                OWNER（400）
     */
    void removeWorkspaceMember(Long workspaceId, Long userId);

    /**
     * 转让空间所有权：当前 OWNER 降级为 MEMBER，目标 MEMBER 提升为 OWNER。
     *
     * <p>
     * 业务规则：
     * </p>
     * <ul>
     * <li>空间必须存在且未逻辑删除；</li>
     * <li>空间未归档——归档状态下不允许改动成员关系，与添加/移出成员同一口径；</li>
     * <li>操作者必须是当前 OWNER，身份取自 {@link com.knowledgepilot.auth.UserContext}，
     * 不接受外部传入；</li>
     * <li>目标<b>必须已经是该空间 MEMBER</b>，否则 404；不允许转让给非成员
     * （那会绕过添加成员接口的校验链）；</li>
     * <li>目标账号必须存在且启用，不能把所有权交给已禁用的账号；</li>
     * <li>转让后原 OWNER 降级为 MEMBER，仍是空间成员（列表里还能看到），
     * 但失去管理权；<b>creator_id 不变</b>——创建者只作审计，与当前所有者无关。</li>
     * </ul>
     *
     * <p>
     * <b>幂等语义：</b>目标已经是 OWNER（当前模型下即"转让给自己"）时不写库，直接返回成功，
     * 与归档/恢复接口"目标状态已达成即幂等成功"同一口径。
     * </p>
     *
     * <p>
     * <b>事务与并发：</b>两条 UPDATE 改两行，必须原子——不加事务会留下
     * "0 个 OWNER"或"2 个 OWNER"的坏状态。每条 UPDATE 的 WHERE 都按主键 + 期望的旧角色（CAS）
     * 锁定具体那一行；第二条失败时抛异常触发回滚，第一条的降级一并撤销。
     * </p>
     *
     * @param workspaceId 空间ID
     * @param request     转让请求（新所有者的 userId）
     * @return 新 OWNER 的成员展示对象
     * @throws com.knowledgepilot.common.BizException 空间不存在（404）、非成员或非 OWNER（403）、
     *                                                操作者账号被禁用（403）、空间已归档（403）、
     *                                                目标不是成员或账号不存在（404）、目标账号被禁用（403）、
     *                                                目标角色不是 MEMBER（400）、并发冲突（409）
     */
    WorkspaceMemberVO transferWorkspaceOwner(Long workspaceId, WorkspaceOwnerTransferRequest request);
}
