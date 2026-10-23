package com.knowledgepilot.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.knowledgepilot.common.Result;
import com.knowledgepilot.dto.WorkspaceMemberAddRequest;
import com.knowledgepilot.dto.WorkspaceOwnerTransferRequest;
import com.knowledgepilot.service.WorkspaceMemberService;
import com.knowledgepilot.vo.WorkspaceMemberVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 空间成员接口。
 *
 * <p>
 * 成员是空间的子资源。本类三个端点都改动 workspace_member 表，故集中在此：
 * 添加 {@code POST /members}、移出 {@code DELETE /members/{userId}}、
 * 转让所有权 {@code POST /owner}（所有者是空间的属性，不挂在 /members 下）。
 * </p>
 */
@Tag(name = "空间成员", description = "空间成员的添加、移出与所有权转让接口")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class WorkspaceMemberController {

    private final WorkspaceMemberService workspaceMemberService;

    public WorkspaceMemberController(WorkspaceMemberService workspaceMemberService) {
        this.workspaceMemberService = workspaceMemberService;
    }

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
    @PostMapping("/members")
    public Result<WorkspaceMemberVO> addWorkspaceMember(@PathVariable("workspaceId") Long workspaceId,
            @Valid @RequestBody WorkspaceMemberAddRequest request) {
        return Result.success(workspaceMemberService.addWorkspaceMember(workspaceId, request));
    }

    /**
     * 移出空间成员。
     *
     * <p>
     * 处理链路：@PathVariable 绑定路径上的两个 ID → Service 做存在性/OWNER 权限/目标成员校验
     * → 物理删除成员行 → 返回空成功响应。
     * </p>
     *
     * <p>
     * <b>路径说明：</b>成员是空间的子资源，移除某人的路径为
     * {@code DELETE /api/workspaces/{workspaceId}/members/{userId}}。
     * 注意路径变量必须写成 <b>"{userId}"</b>（带花括号才是变量）；写成 {@code "/userId"}
     * 会被当作字面量路径段，客户端只能传字符串 "userId"，接口等于废掉。
     * </p>
     *
     * <p>
     * <b>@PathVariable 的名字必须与路径模板里的变量名完全一致</b>（含大小写），
     * 否则运行期抛 MissingPathVariableException → 500。因此本项目统一<b>显式写出变量名</b>
     * （{@code @PathVariable("userId")}）：不依赖编译期 {@code -parameters} 保留参数名，
     * 名字一眼可见，也不会因重构改了参数名而悄悄失效。
     * </p>
     *
     * <p>
     * <b>归档空间的口径：</b>已归档（status=0）的空间不允许移出成员（403），
     * 与向归档空间添加成员的限制保持一致——归档状态下成员关系不可改动。
     * </p>
     *
     * @param workspaceId 空间ID（URL 路径）
     * @param userId      被移出的用户ID（URL 路径）
     * @return 成功响应（无业务数据）
     */
    @Operation(summary = "移出空间成员", description = "把指定用户移出空间；操作者必须是空间 OWNER，且不能移出 OWNER 本人（需先转让所有权）。已归档空间不允许移出成员")
    @DeleteMapping("/members/{userId}")
    public Result<Void> removeWorkspaceMember(@PathVariable("workspaceId") Long workspaceId,
            @PathVariable("userId") Long userId) {
        workspaceMemberService.removeWorkspaceMember(workspaceId, userId);
        return Result.success();
    }

    /**
     * 转让空间所有权。
     *
     * <p>
     * 处理链路：@Valid 校验请求体 → Service 做空间存在性 / OWNER 鉴权 / 归档 / 目标成员与账号
     * 五类校验 → 事务内两次 CAS（旧 OWNER 降级、目标 MEMBER 提升）→ 返回新 OWNER 的成员信息。
     * </p>
     *
     * <p>
     * <b>路径说明：</b>所有者是空间的属性，路径为 {@code POST /api/workspaces/{workspaceId}/owner}。
     * 类级路径已从 {@code /api/workspaces/{workspaceId}/members} 上移一级到
     * {@code /api/workspaces/{workspaceId}}，因此添加与移出端点的方法级路径分别补成
     * {@code "/members"} 与 {@code "/members/{userId}"}——<b>两者的最终 URL 不变</b>。
     * </p>
     *
     * <p>
     * <b>幂等：</b>目标已经是 OWNER（即转让给自己）时不写库，直接返回成功，
     * 与归档/恢复接口“目标状态已达成即幂等成功”同一口径。
     * </p>
     *
     * @param workspaceId 空间ID（URL 路径）
     * @param request     转让请求体（新所有者的 userId，必须已是该空间 MEMBER）
     * @return 新 OWNER 的成员信息
     */
    @Operation(summary = "转让空间所有权", description = "把空间 OWNER 转让给指定的现有 MEMBER，原 OWNER 降级为 MEMBER。操作者必须是当前 OWNER；目标必须已是该空间 MEMBER 且账号可用；归档空间不允许转让。转让给自己按幂等成功返回")
    @PostMapping("/owner")
    public Result<WorkspaceMemberVO> transferWorkspaceOwner(@PathVariable("workspaceId") Long workspaceId,
            @Valid @RequestBody WorkspaceOwnerTransferRequest request) {
        return Result.success(workspaceMemberService.transferWorkspaceOwner(workspaceId, request));
    }
}
