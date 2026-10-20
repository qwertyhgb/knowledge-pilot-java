package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新工作空间请求体。
 *
 * <p>
 * 采用“整体更新”语义：{@code name} 必填并覆盖原值；
 * {@code description} 传 null 或空白串表示清空简介。
 * 如果将来需要“只改简介、不动名称”的部分更新，应另建 PATCH 语义的 DTO，
 * 不要在这个类上放松 {@code @NotBlank}——否则 null 会同时表示“不修改”和“清空”，语义冲突。
 * </p>
 *
 * <p>
 * 不接受 {@code creatorId} / {@code status} / {@code deleted} 等字段：
 * 创建者不可变，状态与删除标记由服务端维护，避免客户端越权修改。
 * </p>
 */
@Data
@Schema(description = "更新工作空间的请求")
public class WorkspaceUpdateRequest {

    /** 空间名称，1~128 字符；必填，更新后覆盖原名称 */
    @Schema(description = "空间名称，1~128 字符", example = "研发中心知识库")
    @NotBlank(message = "空间名称不能为空")
    @Size(min = 1, max = 128, message = "空间名称长度必须在1-128字符之间")
    private String name;

    /** 空间简介，可空；null 或空白串表示清空简介，Service 落库前统一 trim */
    @Schema(description = "空间简介，可空；不传或传空表示清空", example = "存放核心架构规范与开发指南", nullable = true)
    @Size(max = 512, message = "空间简介长度不能超过512字符")
    private String description;
}
