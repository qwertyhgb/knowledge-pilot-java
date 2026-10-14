package com.knowledgepilot.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页查询结果出参（View Object）。
 *
 * <p>承载当前页数据与分页状态，供各业务列表接口复用；Controller 层典型用法：
 * {@code Result.success(PageResult.of(total, pageNum, pageSize, records))}，
 * 最终响应形如 {@code {"code":0,"message":"ok","data":{...分页结构...}}}。</p>
 *
 * <p><b>为什么不直接返回 MyBatis-Plus 的 {@code IPage}？</b>
 * IPage 是 ORM 层对象，直接序列化会把 current/size 等内部字段名一并暴露给前端，
 * 且字段随 MP 版本变化——属于接口契约泄漏。本类用稳定、可预期的结构收口，
 * 静态工厂也不接收 IPage，保证 vo 层不依赖任何 ORM 类型。</p>
 *
 * <p><b>Lombok 说明：</b>{@code @Builder} 会“吞掉”默认无参构造器，必须搭配
 * {@code @NoArgsConstructor}（Jackson 反序列化需要）与 {@code @AllArgsConstructor}
 * （Builder 的 build() 依赖全参构造），三者缺一不可。</p>
 *
 * @param <T> 记录元素类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> {

    @Schema(description = "总记录数，用于前端渲染“共 N 条”", example = "42")
    private long total;

    @Schema(description = "当前页码，从 1 开始", example = "1")
    private long pageNum;

    @Schema(description = "每页条数", example = "20")
    private long pageSize;

    @Schema(description = "总页数；总记录数为 0 时为 0", example = "3")
    private long pages;

    @Schema(description = "当前页数据列表，无数据时为空数组（永不为 null）")
    private List<T> records;

    /**
     * 构建分页结果（推荐入口）。
     *
     * @param total    总记录数
     * @param pageNum  当前页码，从 1 开始
     * @param pageSize 每页条数
     * @param records  当前页数据，允许为 null（会被替换为不可变空集合，保证前端收到 [] 而非 null）
     * @param <T>      记录元素类型
     * @return 分页结果对象
     */
    public static <T> PageResult<T> of(long total, long pageNum, long pageSize, List<T> records) {
        return PageResult.<T>builder()
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                // 向上取整算总页数；pageSize 非法（0/负数）时置 0，避免除零
                .pages(pageSize <= 0 ? 0L : (total + pageSize - 1) / pageSize)
                .records(records == null ? List.of() : records)
                .build();
    }

    /**
     * 构建空分页结果（total=0、pages=0、records=[]）。
     *
     * <p>典型场景：查询条件没匹配到任何数据，让调用方不必自己拼 {@code of(0, ...)}。</p>
     *
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * @param <T>      记录元素类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return of(0L, pageNum, pageSize, List.of());
    }
}
