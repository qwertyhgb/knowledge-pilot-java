package com.knowledgepilot.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledgepilot.entity.Workspace;

/**
 * 工作空间 Mapper。
 *
 * <p>
 * 基础 CRUD 继承 {@link BaseMapper}；跨表分页查询用 {@code @Select} 手写，
 * 原因见各方法注释。
 * </p>
 *
 * <p>
 * 注意：实体带 {@code @TableLogic}，BaseMapper 生成的 {@code deleteById}
 * 是逻辑删除（UPDATE deleted=1）；但<b>手写 SQL 不受其影响</b>，需要自己显式写
 * {@code deleted = 0}。
 * </p>
 */
public interface WorkspaceMapper extends BaseMapper<Workspace> {

  /**
   * 分页查询「某用户加入的所有工作空间」（含他作为 OWNER 的空间）。
   *
   * <p>
   * <b>为什么用 JOIN 而不是两次查询？</b>先查成员表拿到空间 id 集合、
   * 再 IN 查空间也能实现，但分页不准确（必须先把该用户的成员记录全捞进内存
   * 才能对 id 集合分页），且 IN 列表没有上限。JOIN 交给数据库一次算完，
   * 分页天然正确。
   * </p>
   *
   * <p>
   * <b>为什么必须手写 {@code w.deleted = 0}？</b>{@code @TableLogic} 只对
   * BaseMapper / Wrapper 生成的 SQL 生效，注解上的原始 SQL 不会被追加逻辑删除
   * 条件，漏写会让已软删的空间混进结果集。（workspace_member 无 deleted 列，
   * 成员关系物理删除，无需过滤。）
   * </p>
   *
   * <p>
   * <b>为什么不过滤 {@code w.status}（归档停用）？</b>这是确认过的产品决策：
   * 归档（{@code status=0}）的空间<b>仍然返回</b>，由前端依据 VO 里的 status 置灰或折叠。
   * 理由：归档是可恢复状态，过滤掉会让用户"找不到自己的空间"，也与"归档 ≠ 删除"的语义不符。
   * 这与 {@code WorkspaceMemberServiceImpl} 禁止向归档空间添加成员并不矛盾——<b>看得见，但改不了</b>。
   * </p>
   *
   * @param page   分页参数，由 MybatisPlusConfig 的 PaginationInnerInterceptor 处理（单页上限
   *               200）
   * @param userId 用户 ID
   * @return 分页结果，列名按 map-underscore-to-camel-case 映射到实体属性
   */
  @Select("""
      SELECT w.id,
             w.name,
             w.description,
             w.creator_id,
             w.status,
             w.create_time,
             w.update_time
      FROM workspace w
               INNER JOIN workspace_member m ON m.workspace_id = w.id
      WHERE m.user_id = #{userId}
        AND w.deleted = 0
      ORDER BY m.create_time DESC, w.id DESC
      """)
  IPage<Workspace> selectPageByMember(IPage<Workspace> page, @Param("userId") Long userId);
}
