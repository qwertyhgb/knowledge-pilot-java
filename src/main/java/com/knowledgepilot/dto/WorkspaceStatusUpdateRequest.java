package com.knowledgepilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新工作空间状态的请求体（归档 / 恢复）。
 *
 * <p>
 * <b>为什么是「一个接口 + 目标状态」而不是 archive / unarchive 两个动作接口？</b>
 * 语义定为“把状态<b>置为</b>目标值”而不是“执行某个动作”，天然幂等：归档两次、
 * 恢复两次都返回成功，不会因重复操作报错。若是两个动作接口，“重复归档”还得再定义
 * 是报 409 还是算成功。这与 HTTP PUT 的“设定目标状态”语义一致。
 * </p>
 *
 * <p>
 * <b>为什么 status 用 {@code Integer} 而不是 {@code int}？</b>基本类型 int 在 JSON
 * 漏传时会取默认值 0，而 0 恰好是合法业务值（归档）——漏传字段会被静默当成“归档”，
 * 这是能改错数据的坑。用 {@code Integer} 让漏传变成 null，由 {@code @NotNull} 拦成 400。
 * </p>
 *
 * <p>
 * <b>校验策略：</b>{@code @NotNull + @Min(0) + @Max(1)} 是白名单，把“状态机只有
 * 两态（0-归档，1-正常）”锁死在请求边界，非法状态值根本进不了 Service。
 * 依赖前提：Controller 必须写 {@code @Valid @RequestBody}，否则注解不生效，
 * Service 里的自动拆箱（Integer → int）遇到 null 会 NPE 变成 500 而不是 400。
 * </p>
 *
 * <p>
 * 不接受 name / description：内容修改走 {@link WorkspaceUpdateRequest}。
 * 状态与内容分离，是因为两者鉴权口径不同——状态操作<b>允许</b>在归档空间上执行
 * （恢复 0→1 必须能操作），内容修改则被归档挡住（“看得见、改不了”）。
 * </p>
 */
@Data
@Schema(description = "更新工作空间状态的请求（归档/恢复）")
public class WorkspaceStatusUpdateRequest {

    /** 目标状态：0-归档停用，1-正常；白名单校验保证状态只在这两态之间迁移 */
    @Schema(description = "目标状态：0-归档停用，1-正常", example = "0")
    @NotNull(message = "目标状态不能为空")
    @Min(value = 0, message = "目标状态只能为 0（归档）或 1（正常）")
    @Max(value = 1, message = "目标状态只能为 0（归档）或 1（正常）")
    private Integer status;
}
