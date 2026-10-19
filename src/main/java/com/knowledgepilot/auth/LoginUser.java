package com.knowledgepilot.auth;

/**
 * 当前登录用户（身份快照）。
 *
 * <p>
 * 表示“这次请求的操作者是谁”，只保留鉴权与业务所需的最小身份信息，
 * 不复用 {@code UserAccount} 实体，避免把密码散列、审计字段等敏感/无关数据带进请求上下文。
 * 预期用法：鉴权逻辑校验会话通过后构造本对象，放入请求上下文（如 ThreadLocal / 拦截器 attribute），
 * 业务代码据此取当前用户（如创建空间时填 ownerId、判断成员归属）。
 * </p>
 */
public record LoginUser(Long id, String username) {

}
