package com.knowledgepilot.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.knowledgepilot.common.Result;
import com.knowledgepilot.dto.WorkspaceMemberAddRequest;
import com.knowledgepilot.service.WorkspaceMemberService;
import com.knowledgepilot.vo.WorkspaceMemberVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 空间成员接口。
 *
 * <p>
 * 成员是空间的子资源，路径设计为 /workspaces/{workspaceId}/members。
 * </p>
 */
@Tag(name = "空间成员", description = "空间成员添加与管理相关接口")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/members")
@RequiredArgsConstructor
public class WorkspaceMemberController {

    private final WorkspaceMemberService workspaceMemberService;

    /**
     * 向指定空间添加成员。
     *
     * <p>
     * 处理链路：@Valid 校验请求体 → Service 做存在性/权限/角色/重复四类校验
     * → uk_ws_user 唯一索引兜底并发 → 返回新成员信息。
     * </p>
     *
     * @param workspaceId 目标空间ID（URL 路径）
     * @param request     添加成员请求体
     * @return 新成员信息
     */
    @Operation(summary = "添加空间成员", description = "把指定用户加入空间并指定角色，操作者必须是空间 OWNER")
    @PostMapping
    public Result<WorkspaceMemberVO> addWorkspaceMember(@PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceMemberAddRequest request) {
        return Result.success(workspaceMemberService.addWorkspaceMember(workspaceId, request));
    }
}
