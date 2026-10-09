package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "知识文档新增/修改入参")
public record KnowledgeDocRequest(
        @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "正文内容")
        @Size(max = 60000) String content,

        @Schema(description = "分类，默认 general", maxLength = 64)
        @Size(max = 64) String category,

        @Schema(description = "标签，英文逗号分隔", maxLength = 255)
        @Size(max = 255) String tags,

        @Schema(description = "状态：0-草稿，1-已发布", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(0) @Max(1) Integer status) {
}
