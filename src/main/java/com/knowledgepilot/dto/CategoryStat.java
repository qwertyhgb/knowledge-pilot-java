package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "分类统计")
public class CategoryStat {

    @Schema(description = "分类")
    private String category;

    @Schema(description = "文档总数")
    private long total;

    @Schema(description = "已发布数")
    private long published;
}
