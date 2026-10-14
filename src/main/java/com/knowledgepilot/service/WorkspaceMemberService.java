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
     *     指定 OWNER（OWNER 应由空间创建时确定或日后经转让流程变更）；</li>
     * <li>同一用户不能重复加入同一空间（uk_ws_user 联合唯一索引兜底并发）。</li>
     * </ul>
     *
     * @param workspaceId 目标空间ID
     * @param request     添加成员请求
     * @return 新成员展示对象
     * @throws com.knowledgepilot.common.BizException 任一业务规则不满足时抛出（403/404/409）
     */
    WorkspaceMemberVO addWorkspaceMember(Long workspaceId, WorkspaceMemberAddRequest request);
}
