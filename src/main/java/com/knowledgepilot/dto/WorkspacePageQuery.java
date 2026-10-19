package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工作空间列表分页查询条件。
 *
 * <p>
 * GET 请求没有请求体，URL Query 参数（?memberId=1&page=1&size=20）由
 * {@code @ModelAttribute} 自动绑定到本对象；page/size 未传时使用字段默认值。
 * 用对象收口参数，避免多个入参散落在 Controller 方法签名中。
 * </p>
 */
@Data
@Schema(description = "工作空间列表分页查询条件")
public class WorkspacePageQuery {

    /** 成员用户ID（关联 user_account.id），限定为正整数 */
    @Schema(description = "成员用户ID（关联 user_account.id）", example = "1")
    @NotNull(message = "不能为空")
    @Min(value = 1, message = "必须是正整数")
    private Long memberId;

    /** 页码，从 1 开始；未传时默认第 1 页 */
    @Schema(description = "页码，从 1 开始，默认 1", example = "1")
    @Min(value = 1, message = "必须大于等于 1")
    private long page = 1;

    /** 每页条数，默认 20；上限 200 与分页插件 maxLimit 保持一致 */
    @Schema(description = "每页条数，默认 20，上限 200", example = "20")
    @Min(value = 1, message = "必须大于等于 1")
    @Max(value = 200, message = "不能超过 200")
    private long size = 20;
}
