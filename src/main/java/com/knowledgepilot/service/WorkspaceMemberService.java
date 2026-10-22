package com.knowledgepilot.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.knowledgepilot.dto.WorkspaceMemberAddRequest;
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
     *                                               非 OWNER（403）、目标成员不存在（404）、试图移出 OWNER（400）
     */
    void removeWorkspaceMember(Long workspaceId, Long userId);
}
