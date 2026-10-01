package org.icedamericanomall.user.domain.port;

import org.icedamericanomall.user.domain.model.User;

/**
 * 新用户建档能力（领域层定义，应用层实现）。
 *
 * <p>存在的意义：{@code UserProvisioner} 负责"查找或创建"的<b>规则</b>（先查短路、
 * 业务键互斥锁、唯一键冲突回查），但"建档"本身还包含默认昵称、默认 RBAC 角色
 * （user 行 + sys_user_role 的<b>同一本地事务</b>）等应用编排动作 —— 规则留在领域层，
 * 落库编排经本端口倒置给应用层实现（{@code UserRegistrar}），领域层保持零框架依赖。
 */
public interface NewUserRegistrar {

    /**
     * 落库新用户（补齐默认昵称 → 插入 user 行 → 授予默认角色），事务原子。
     *
     * @param user 待建档用户（{@code userId} 业务键已由领域工厂生成）
     * @return 落库后的用户（含技术主键）
     */
    User register(User user);
}
