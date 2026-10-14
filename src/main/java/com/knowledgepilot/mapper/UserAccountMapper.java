package com.knowledgepilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgepilot.entity.UserAccount;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户账号 Mapper。
 *
 * <p>基础 CRUD 继承 {@link BaseMapper}；下面两个方法是"墓碑查询"，为修掉
 * 「逻辑删除 + 唯一索引」冲突而存在，属于本接口唯一需要手写 SQL 的地方，
 * 原因见各方法注释。</p>
 *
 * <p>注意：实体带 {@code @TableLogic}，{@code deleteById} 生成的是逻辑删除
 * （UPDATE deleted=1），不是物理删除。</p>
 */
public interface UserAccountMapper extends BaseMapper<UserAccount> {

    /**
     * 查询是否存在"已被逻辑删除"的同名账号（墓碑行），返回其 id，不存在返回 null。
     *
     * <p><b>为什么必须手写 SQL？</b>MyBatis-Plus 的 {@code lambdaQuery()} 会自动追加
     * {@code deleted = 0}，所以用普通查询永远看不到墓碑行；而库表的唯一索引
     * {@code uk_username} 是物理存在的，墓碑行依然占着这个用户名。只有绕过逻辑删除
     * 才能查出来，因此这里显式写出 {@code deleted = 1}（与全局配置
     * {@code logic-delete-value: 1} 一致；注解上的原始 SQL 不会被 MP 追加 deleted=0）。</p>
     *
     * @param username 已规范化的登录名（调用方负责 trim）
     * @return 墓碑行主键，无则为 null
     */
    @Select("SELECT id FROM user_account WHERE username = #{username} AND deleted = 1 LIMIT 1")
    Long selectTombstoneIdByUsername(@Param("username") String username);

    /**
     * 查询是否存在"已被逻辑删除"的同邮箱账号（墓碑行），返回其 id，不存在返回 null。
     *
     * <p>同 {@link #selectTombstoneIdByUsername(String)}：绕过 {@code deleted = 0} 的自动过滤。</p>
     *
     * @param email 已规范化的邮箱（调用方负责 trim + 转小写）
     * @return 墓碑行主键，无则为 null
     */
    @Select("SELECT id FROM user_account WHERE email = #{email} AND deleted = 1 LIMIT 1")
    Long selectTombstoneIdByEmail(@Param("email") String email);
}
