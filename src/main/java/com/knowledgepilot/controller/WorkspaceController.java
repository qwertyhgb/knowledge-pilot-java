package com.knowledgepilot.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.knowledgepilot.common.Result;
import com.knowledgepilot.dto.WorkspaceCreateRequest;
import com.knowledgepilot.dto.WorkspacePageQuery;
import com.knowledgepilot.dto.WorkspaceStatusUpdateRequest;
import com.knowledgepilot.dto.WorkspaceUpdateRequest;
import com.knowledgepilot.service.WorkspaceService;
import com.knowledgepilot.vo.PageResult;
import com.knowledgepilot.vo.WorkspaceVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 工作空间接口。
 *
 * <p>
 * 只做 HTTP 协议转换：参数校验、身份透传与统一响应；
 * 创建者/操作者身份一律由 Service 从 {@code UserContext} 获取，
 * 本类不接收也不传递任何用户身份字段。
 * </p>
 */
@Tag(name = "工作空间", description = "工作空间的创建、查询与更新接口")
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * 创建工作空间。
     *
     * <p>
     * 处理链路：
     * </p>
     * <ol>
     * <li>{@code @Valid} 触发 {@link WorkspaceCreateRequest} 校验（名称非空、长度），失败转
     * 400；</li>
     * <li>Service 从 {@code UserContext} 取当前登录用户作为创建者，不接受请求体传入，避免伪造；</li>
     * <li>同一事务内写 workspace 与 workspace_member(role=OWNER) 两行；</li>
     * <li>返回新空间的展示对象（不含 deleted 等内部字段）。</li>
     * </ol>
     *
     * @param request 创建请求（名称必填，简介可空）
     * @return 新建工作空间信息
     */
    @Operation(summary = "创建工作空间", description = "创建工作空间并自动将创建者添加为 OWNER 角色成员")
    @PostMapping
    public Result<WorkspaceVO> createWorkspace(@Valid @RequestBody WorkspaceCreateRequest request) {
        return Result.success(workspaceService.createWorkspace(request));
    }

    /**
     * 查询当前登录用户加入的工作空间。
     *
     * <p>
     * 参数绑定与校验说明：
     * </p>
     * <ul>
     * <li>GET 请求无请求体，采用 {@code @ModelAttribute} 将 URL Query
     * 参数（?page=1&size=20）
     * 自动绑定到 {@link WorkspacePageQuery} 对象属性上，避免多个入参散落在方法签名中；</li>
     * <li>配合 {@code @Valid} 触发 DTO 内部的 JSR-380 参数校验（正整数、单页上限），失败统一由全局异常处理器转为
     * 400；</li>
     * <li>同时保证 Swagger / OpenAPI 正确将参数识别并渲染为独立的 Query Parameters 输入框，而非错误的 Request
     * Body。</li>
     * </ul>
     *
     * <p>
     * 身份说明：查询主体固定为当前登录用户，取自 {@code UserContext}（鉴权拦截器写入），
     * 请求不再接受 memberId，从接口层面杜绝越权查询他人空间（IDOR）。
     * </p>
     *
     * @param query 分页查询条件（从 URL Query 参数解析）
     * @return 分页结果
     */
    @Operation(summary = "查询当前用户加入的工作空间", description = "查询当前登录用户加入的所有工作空间（含自己创建的，OWNER 空间同样返回）。身份取自登录会话，不接受 memberId 传参，杜绝越权查询他人空间")
    @GetMapping
    public Result<PageResult<WorkspaceVO>> listMyWorkspaces(@Valid @ModelAttribute WorkspacePageQuery query) {
        return Result.success(workspaceService.listMyWorkspaces(
                query.getPage(), query.getSize()));
    }

    /**
     * 更新工作空间。
     *
     * <p>
     * 处理链路：
     * </p>
     * <ol>
     * <li>{@code @Valid} 触发 {@link WorkspaceUpdateRequest} 校验（名称非空、长度），
     * 失败由 {@code GlobalExceptionHandler} 统一转成 400；</li>
     * <li>Service 从 {@code UserContext} 取当前登录用户作为操作者，不接受请求体传入；</li>
     * <li>空间必须存在且未归档，操作者必须是该空间 OWNER；</li>
     * <li>整体更新 name 与 description，description 传空表示清空。</li>
     * </ol>
     *
     * @param workspaceId 空间ID（路径参数）
     * @param request     更新请求（名称必填，简介可空）
     * @return 更新后的工作空间信息
     */
    @Operation(summary = "更新工作空间", description = "整体更新空间名称与简介；仅该空间 OWNER 可操作，归档空间不可修改。creatorId/status 不接受客户端修改")
    @PutMapping("/{workspaceId}")
    public Result<WorkspaceVO> updateWorkspace(@PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceUpdateRequest request) {
        return Result.success(workspaceService.updateWorkspace(workspaceId, request));
    }

    /**
     * 更新工作空间状态（归档 / 恢复）。
     *
     * <p>
     * 处理链路：
     * </p>
     * <ol>
     * <li>{@code @Valid} 触发 {@link WorkspaceStatusUpdateRequest} 白名单校验
     * （status 非空且只能是 0/1），失败由 {@code GlobalExceptionHandler} 统一转成 400；</li>
     * <li>Service 从 {@code UserContext} 取当前登录用户作为操作者，不接受请求体传入；</li>
     * <li>空间必须存在且未逻辑删除；<b>归档空间也允许执行本操作</b>——恢复（0→1）必须能
     * 在归档空间上执行。与 {@link #updateWorkspace}“归档不可改”并不矛盾：“看得见、
     * 改不了”约束的是内容修改，状态机自身的归档/恢复不受该约束；</li>
     * <li>操作者必须是该空间 OWNER；</li>
     * <li>语义是“把状态置为目标值”，接口幂等：归档两次 / 恢复两次都返回成功。
     * 并发冲突由 CAS 乐观并发兜底，未打中时重新读库分辨幂等成功 / 404 / 409。</li>
     * </ol>
     *
     * @param workspaceId 空间ID（路径参数）
     * @param request     目标状态请求（status：0-归档停用，1-正常）
     * @return 更新后的工作空间信息
     */
    @Operation(summary = "归档或恢复工作空间", description = "把空间状态置为目标值：0-归档停用，1-正常。仅该空间 OWNER 可操作；归档空间也允许恢复。接口幂等，重复操作返回成功")
    @PutMapping("/{workspaceId}/status")
    public Result<WorkspaceVO> updateWorkspaceStatus(@PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceStatusUpdateRequest request) {
        return Result.success(workspaceService.updateWorkspaceStatus(workspaceId, request));
    }
}
