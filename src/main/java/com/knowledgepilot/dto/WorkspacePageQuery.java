package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 工作空间列表分页查询条件。
 *
 * <p>
 * GET 请求没有请求体，URL Query 参数（?page=1&size=20）由
 * {@code @ModelAttribute} 自动绑定到本对象；page/size 未传时使用字段默认值。
 * 用对象收口参数，避免多个入参散落在 Controller 方法签名中。
 * </p>
 *
 * <p>
 * <b>为什么没有 memberId？</b>本接口查的是“当前登录用户”加入的空间，主体身份只能来自
 * {@link com.knowledgepilot.auth.UserContext}（由鉴权拦截器写入），绝不能由客户端通过
 * 查询参数传入——否则改一下 userId 就能查遍全站用户的空间（IDOR）。
 * 即使客户端仍带 {@code ?memberId=999}，也会被直接忽略。
 * </p>
 */
@Data
@Schema(description = "工作空间列表分页查询条件")
public class WorkspacePageQuery {

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
