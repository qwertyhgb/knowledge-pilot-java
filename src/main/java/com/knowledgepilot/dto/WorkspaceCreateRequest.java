package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建工作空间请求体。
 *
 * <p>
 * 只承载用户可填写的字段：名称与简介。
 * 创建者（creator_id）与初始状态（status）都由服务端决定：
 * 创建者来自登录上下文，状态固定为 1-正常，因此不出现在请求体中，
 * 避免客户端伪造创建者或状态。
 * </p>
 */
@Data
@Schema(description = "创建工作空间的请求")
public class WorkspaceCreateRequest {

    /** 空间名称，1~128 字符；允许重名，唯一性不作为业务约束 */
    @Schema(description = "空间名称", example = "研发中心知识库")
    @NotBlank(message = "空间名称不能为空")
    @Size(min = 1, max = 128, message = "空间名称长度必须在1-128字符之间")
    private String name;

    /** 空间简介，可空；落库前由 Service 统一 trim，空白串归一为 null */
    @Schema(description = "空间简介，可空", example = "存放核心架构规范与开发指南", nullable = true)
    @Size(max = 512, message = "空间简介长度不能超过512字符")
    private String description;
}
