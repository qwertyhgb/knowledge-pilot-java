package com.knowledgepilot.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.knowledgepilot.dto.WorkspaceCreateRequest;
import com.knowledgepilot.dto.WorkspaceUpdateRequest;
import com.knowledgepilot.entity.Workspace;
import com.knowledgepilot.vo.PageResult;
import com.knowledgepilot.vo.WorkspaceVO;

/**
 * 工作空间业务服务接口。
 */
public interface WorkspaceService extends IService<Workspace> {

    /**
     * 创建工作空间。
     *
     * <p>业务规则：</p>
     * <ul>
     *   <li>创建者账号必须存在且状态正常（status=1）；</li>
     *   <li>空间创建成功后，自动在成员表中绑定创建者为 OWNER 角色；</li>
     * </ul>
     *
     * @param request 创建工作空间请求
     * @return 脱敏后的工作空间展示对象
     */
    WorkspaceVO createWorkspace(WorkspaceCreateRequest request);

    /**
     * 分页查询当前登录用户加入的所有工作空间。
     *
     * <p>「加入」以 workspace_member 为准：自己创建的空间在创建时会自动写入一条
     * role='OWNER' 的成员记录，因此同样会出现在结果里；已逻辑删除的空间不返回。</p>
     *
     * <p>鉴权说明：查询主体固定为“当前登录用户”，身份由实现类从
     * {@link com.knowledgepilot.auth.UserContext} 取，不接受外部传入 userId，
     * 从接口层面杜绝越权查询他人空间（IDOR）。</p>
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页后的工作空间列表；未加入任何空间时返回空分页（total=0、records=[]）
     */
    PageResult<WorkspaceVO> listMyWorkspaces(long pageNum, long pageSize);

    /**
     * 更新工作空间。
     *
     * <p>业务规则：</p>
     * <ul>
     *   <li>空间必须存在且未归档（status=1）；</li>
     *   <li>操作者必须是该空间的 OWNER，身份取自
     *       {@link com.knowledgepilot.auth.UserContext}，不接受外部传入；</li>
     *   <li>整体更新：name 必填并覆盖原值，description 传 null 或空白串表示清空；</li>
     *   <li>creatorId、status、deleted 不接受客户端修改。</li>
     * </ul>
     *
     * @param workspaceId 空间ID
     * @param request     更新请求
     * @return 更新后的工作空间展示对象
     * @throws com.knowledgepilot.common.BizException 空间不存在（404）、已归档或非 OWNER（403）
     */
    WorkspaceVO updateWorkspace(Long workspaceId, WorkspaceUpdateRequest request);
}
