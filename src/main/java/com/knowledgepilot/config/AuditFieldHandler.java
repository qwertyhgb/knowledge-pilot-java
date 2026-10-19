package com.knowledgepilot.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * 审计字段自动填充处理器。
 *
 * <p>
 * 实现 MyBatis-Plus 的 {@link MetaObjectHandler}，在通用 CRUD 执行前把
 * {@code createTime} / {@code updateTime} 写入实体，业务代码不需要手动 set。
 * 库表的 {@code DEFAULT CURRENT_TIMESTAMP} 只是兜底。
 * </p>
 *
 * <p>
 * <b>边界：</b>本处理器只对 MyBatis-Plus 的通用 CRUD / Wrapper 生效，
 * 手写 SQL（{@code @Select}/{@code @Update}/XML）不会触发，需要自己维护时间字段。
 * </p>
 *
 * <p>
 * 使用 {@code strictInsertFill} / {@code strictUpdateFill}（严格模式）：
 * 仅在字段为 null 时填充，不覆盖调用方显式设置的值，便于数据迁移与补录场景。
 * </p>
 */
@Component
public class AuditFieldHandler implements MetaObjectHandler {

    /**
     * 插入时填充：创建时间与更新时间取同一时刻，保证新记录两者一致。
     *
     * @param metaObject 待插入实体
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    /**
     * 更新时填充：只刷新更新时间，创建时间保持不变。
     *
     * @param metaObject 待更新实体
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
