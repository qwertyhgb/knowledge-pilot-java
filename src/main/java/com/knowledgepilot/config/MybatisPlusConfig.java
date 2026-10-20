package com.knowledgepilot.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p>
 * 这里装配两个全局拦截器，都是“数据库访问的兜底防线”，业务代码不需要感知。
 * </p>
 *
 * <p>
 * <b>作用范围：</b>分页与防全表更新都是 MyBatis 拦截器，会作用于所有经过 MyBatis
 * 执行的 SQL（含 BaseMapper、注解 SQL、XML）。<br>
 * 不受影响的是另外两项全局能力：{@code @TableLogic} 逻辑删除与
 * {@link AuditFieldHandler} 自动填充——它们不是插件，只处理 MP 生成的 SQL，
 * 手写 SQL 必须自己写 {@code deleted = 0} 并维护时间字段。
 * </p>
 *
 * <p>
 * {@code proxyBeanMethods = false} 表示轻量配置模式：不为配置类生成 CGLIB 代理，
 * 启动更快；本类没有“一个 @Bean 方法调用另一个 @Bean 方法”的需求，可以安全关闭。
 * </p>
 *
 * <p><b>插件顺序：</b>分页插件放在防全表更新插件之前，与 MP 官方推荐顺序
 * （多租户 → 动态表名 → 分页 → 乐观锁 → 防全表更新）一致；顺序错误会导致 SQL 改写结果不符预期。</p>
 */
@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfig {

    /**
     * 装配全局拦截器链。
     *
     * @return MyBatis-Plus 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件：显式指定 MySQL 方言；单页最多 200 条，超出会被截断到 200（不报错）
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(200L);
        // overflow=false：页码超过总页数时返回空页，而不是“回退到第一页”造成误导
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        // 防全表更新/删除：拦截没有 where 条件的 update/delete；
        // 注意 WHERE 1=1 这类恒真条件仍会放行，TRUNCATE 等 DDL 不在拦截范围
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
