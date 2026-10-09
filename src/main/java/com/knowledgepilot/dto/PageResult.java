package com.knowledgepilot.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "分页结果")
public record PageResult<T>(
        @Schema(description = "总条数") long total,
        @Schema(description = "当前页") long page,
        @Schema(description = "每页条数") long size,
        @Schema(description = "数据") List<T> records) {

    public static <T> PageResult<T> of(IPage<T> p) {
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords());
    }
}
