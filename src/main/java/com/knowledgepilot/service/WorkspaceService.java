package com.knowledgepilot.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.knowledgepilot.dto.WorkspaceCreateRequest;
import com.knowledgepilot.dto.WorkspaceStatusUpdateRequest;
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
     * <p>
     * 业务规则：
     * </p>
     * <ul>
     * <li>创建者账号必须存在且状态正常（status=1）；</li>
     * <li>空间创建成功后，自动在成员表中绑定创建者为 OWNER 角色；</li>
     * </ul>
     *
     * @param request 创建工作空间请求
     * @return 脱敏后的工作空间展示对象
     */
    WorkspaceVO createWorkspace(WorkspaceCreateRequest request);

    /**
     * 分页查询当前登录用户加入的所有工作空间。
     *
     * <p>
     * 「加入」以 workspace_member 为准：自己创建的空间在创建时会自动写入一条
     * role='OWNER' 的成员记录，因此同样会出现在结果里；已逻辑删除的空间不返回。
     * </p>
     *
     * <p>
     * 鉴权说明：查询主体固定为“当前登录用户”，身份由实现类从
     * {@link com.knowledgepilot.auth.UserContext} 取，不接受外部传入 userId，
     * 从接口层面杜绝越权查询他人空间（IDOR）。
     * </p>
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页后的工作空间列表；未加入任何空间时返回空分页（total=0、records=[]）
     */
    PageResult<WorkspaceVO> listMyWorkspaces(long pageNum, long pageSize);

    /**
     * 更新工作空间。
     *
     * <p>
     * 业务规则：
     * </p>
     * <ul>
     * <li>空间必须存在且未归档（status=1）；</li>
     * <li>操作者必须是该空间的 OWNER，身份取自
     * {@link com.knowledgepilot.auth.UserContext}，不接受外部传入；</li>
     * <li>整体更新：name 必填并覆盖原值，description 传 null 或空白串表示清空；</li>
     * <li>creatorId、status、deleted 不接受客户端修改。</li>
     * </ul>
     *
     * @param workspaceId 空间ID
     * @param request     更新请求
     * @return 更新后的工作空间展示对象
     * @throws com.knowledgepilot.common.BizException 空间不存在（404）、已归档或非 OWNER（403）
     */
    WorkspaceVO updateWorkspace(Long workspaceId, WorkspaceUpdateRequest request);

    /**
     * 更新工作空间状态（归档 / 恢复）。
     *
     * <p>
     * 业务规则：
     * </p>
     * <ul>
     * <li>空间必须存在且未逻辑删除；<b>归档空间也可操作</b>——恢复（0→1）必须能在归档空间上执行。
     * 这与 {@link #updateWorkspace}“归档不可改”的口径并不矛盾：“看得见、改不了”约束的是
     * <b>内容修改</b>，状态机自身的归档/恢复不受该约束；</li>
     * <li>操作者必须是该空间 OWNER，身份取自 {@link com.knowledgepilot.auth.UserContext}，
     * 不接受外部传入；</li>
     * <li>目标状态只能是 0（归档）或 1（正常），由 {@code WorkspaceStatusUpdateRequest} 白名单校验；</li>
     * <li>不接受 name / creatorId / deleted 等其他字段。</li>
     * </ul>
     *
     * <p>
     * <b>幂等语义：</b>当前状态已是目标值时直接返回成功（不报错、不写库），
     * 归档两次 / 恢复两次都返回成功。
     * </p>
     *
     * <p>
     * <b>并发处理（CAS 乐观并发，不加锁）：</b>UPDATE 的 WHERE 里带上期望的旧状态
     * （{@code WHERE id = ? AND status = 期望值}），状态若已被别的请求改过则影响行数为 0，
     * 两个并发请求天然只有一个成功。未打中时<b>重新读库分辨三种情况</b>：
     * 已被并发置为目标值 → 按幂等成功返回；已被删除 → 404；状态变成其他值 → 409 真冲突。
     * </p>
     *
     * <p>
     * 事务说明：方法内只有单条 UPDATE（CAS 未打中时仅做查询），单条 SQL 自带原子性，
     * 不加 {@code @Transactional}。
     * </p>
     *
     * @param workspaceId 空间ID
     * @param request     目标状态请求（status：0-归档，1-正常）
     * @return 更新后的工作空间展示对象
     * @throws com.knowledgepilot.common.BizException 空间不存在（404）、非成员或非 OWNER（403）、
     *                                                操作者账号被禁用（403）、状态被并发修改且未达目标值（409）
     */
    WorkspaceVO updateWorkspaceStatus(Long workspaceId, WorkspaceStatusUpdateRequest request);

    /**
     * 删除工作空间（逻辑删除）。
     *
     * <p>
     * 业务规则：
     * </p>
     * <ul>
     * <li>空间必须存在且未逻辑删除；<b>归档空间也可删除</b>——删除是生命周期操作，
     * 与 {@link #updateWorkspace}“归档不可改内容”并不矛盾。若归档后不能删，空间只能恢复、
     * 无法销毁；</li>
     * <li>操作者必须是该空间 OWNER，身份取自 {@link com.knowledgepilot.auth.UserContext}，
     * 不接受外部传入；</li>
     * <li>走实体上的 {@code @TableLogic}：{@code removeById} 生成
     * {@code UPDATE workspace SET deleted=1 WHERE id=? AND deleted=0}，不是物理 DELETE；</li>
     * <li>不级联删除 {@code workspace_member}：成员关系留给空间恢复时仍有效；成员表本身是物理删除，
     * 这里若顺手 DELETE 成员，恢复后会变成空壳。</li>
     * </ul>
     *
     * <p>
     * <b>再次删除：</b>已逻辑删除的行会被 {@code getById} 自动过滤，第二次删除返回 404。
     * 这与登出“重复也成功”不同：删除的对象是资源本身，资源已不存在就按 404 处理。
     * </p>
     *
     * <p>
     * 并发说明：{@code removeById} 的 WHERE 已带 {@code deleted=0}，两个并发删除只有一个
     * 影响行数为 1；未打中时按 404 返回（另一请求已先删，结果已达成）。
     * 单条 UPDATE 自带原子性，不加 {@code @Transactional}。
     * </p>
     *
     * @param workspaceId 空间ID
     * @throws com.knowledgepilot.common.BizException 空间不存在或已被删除（404）、
     *                                                非成员或非 OWNER（403）、操作者账号被禁用（403）
     */
    void deleteWorkspace(Long workspaceId);
}
