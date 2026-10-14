package com.knowledgepilot.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.knowledgepilot.dto.WorkspaceCreateRequest;
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
     * 分页查询某个用户加入的所有工作空间。
     *
     * <p>「加入」以 workspace_member 为准：自己创建的空间在创建时会自动写入一条
     * role='OWNER' 的成员记录，因此同样会出现在结果里；已逻辑删除的空间不返回。</p>
     *
     * <p>鉴权说明：本方法只负责“给定 userId 查其加入的空间”，不关心调用者是谁；
     * 将来接入登录态后由 Controller 决定传入哪个 userId，本层无需改动。</p>
     *
     * @param userId   用户 ID
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页后的工作空间列表；未加入任何空间时返回空分页（total=0、records=[]）
     * @throws com.knowledgepilot.common.BizException 用户不存在时抛出（404）
     */
    PageResult<WorkspaceVO> listByMemberId(Long userId, long pageNum, long pageSize);
}
