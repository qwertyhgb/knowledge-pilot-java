package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建工作空间请求体。
 */
@Data
@Schema(description = "创建工作空间的请求")
public class WorkspaceCreateRequest {

    @Schema(description = "空间名称", example = "研发中心知识库")
    @NotBlank(message = "空间名称不能为空")
    @Size(min = 1, max = 128, message = "空间名称长度必须在1-128字符之间")
    private String name;

    @Schema(description = "空间简介，可空", example = "存放核心架构规范与开发指南", nullable = true)
    @Size(max = 512, message = "空间简介长度不能超过512字符")
    private String description;
}
