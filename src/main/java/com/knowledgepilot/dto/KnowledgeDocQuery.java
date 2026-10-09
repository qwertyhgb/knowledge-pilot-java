package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "知识文档分页查询条件")
public class KnowledgeDocQuery {

    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    @Min(1)
    private long page = 1;

    @Schema(description = "每页条数，最大 200", defaultValue = "10")
    @Min(1) @Max(200)
    private long size = 10;

    @Schema(description = "标题模糊关键字")
    private String keyword;

    @Schema(description = "分类精确匹配")
    private String category;

    @Schema(description = "状态：0-草稿，1-已发布，不传查全部")
    private Integer status;
}
