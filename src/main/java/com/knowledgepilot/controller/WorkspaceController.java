package com.knowledgepilot.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.knowledgepilot.common.Result;
import com.knowledgepilot.dto.WorkspaceCreateRequest;
import com.knowledgepilot.dto.WorkspacePageQuery;
import com.knowledgepilot.service.WorkspaceService;
import com.knowledgepilot.vo.PageResult;
import com.knowledgepilot.vo.WorkspaceVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 工作空间接口。
 */
@Tag(name = "工作空间", description = "工作空间创建、查询与成员管理相关接口")
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
     * <p>处理链路：</p>
     * <ol>
     *   <li>{@code @Valid} 触发 {@link WorkspaceCreateRequest} 校验（名称非空、长度），失败转 400；</li>
     *   <li>Service 从 {@code UserContext} 取当前登录用户作为创建者，不接受请求体传入，避免伪造；</li>
     *   <li>同一事务内写 workspace 与 workspace_member(role=OWNER) 两行；</li>
     *   <li>返回新空间的展示对象（不含 deleted 等内部字段）。</li>
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
     * 查询某用户加入的工作空间。
     *
     * <p>参数绑定与校验说明：</p>
     * <ul>
     *   <li>GET 请求无请求体，采用 {@code @ModelAttribute} 将 URL Query 参数（?memberId=1&page=1&size=20）
     *       自动绑定到 {@link WorkspacePageQuery} 对象属性上，避免多个入参散落在方法签名中；</li>
     *   <li>配合 {@code @Valid} 触发 DTO 内部的 JSR-380 参数校验（如必填、正整数、单页上限），失败统一由全局异常处理器转为 400；</li>
     *   <li>同时保证 Swagger / OpenAPI 正确将参数识别并渲染为独立的 Query Parameters 输入框，而非错误的 Request Body。</li>
     * </ul>
     *
     * @param query 分页查询条件（从 URL Query 参数解析）
     * @return 分页结果
     */
    @Operation(summary = "查询某用户加入的工作空间",
            description = "按成员用户 ID 分页查询其加入的所有工作空间；自己创建的空间（OWNER）同样包含在内。用户不存在时返回 404")
    @GetMapping
    public Result<PageResult<WorkspaceVO>> listByMemberId(@Valid @ModelAttribute WorkspacePageQuery query) {
        return Result.success(workspaceService.listByMemberId(
                query.getMemberId(), query.getPage(), query.getSize()));
    }
}
