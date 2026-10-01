package org.icedamericanomall.user.app.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.port.NewUserRegistrar;
import org.icedamericanomall.user.domain.repository.RoleRepository;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户建档（应用层）—— "查找或创建"漏斗在新建分支的落库编排：
 * 补齐默认昵称 → 插入 user 行 → 授予默认 RBAC 角色（同一本地事务）。
 *
 * <p>实现领域端口 {@link NewUserRegistrar}：领域服务 {@code UserProvisioner} 负责
 * 幂等规则（先查短路 / 互斥锁 / 冲突回查），本类负责事务内的落库编排
 * （V5.2：注册 / 短信首登 / 邮箱首登 / 微信登录共用同一建档漏斗）。
 *
 * <p>事务说明：user 行与 sys_user_role 的写入保持同一本地事务（原 {@code UserRegistrar}
 * 语义）；事务只覆盖本地库写，不含任何远程调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegistrar implements NewUserRegistrar {

    /** 默认角色 ID —— ROLE_USER */
    private static final Long DEFAULT_ROLE_ID = 1L;

    /** 默认昵称前缀（未提供昵称时生成） */
    private static final String DEFAULT_NICKNAME_PREFIX = "ice_";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /**
     * 落库新用户：补齐默认昵称 → 插入 user 行 → 授予默认角色。
     *
     * @param user 待建档用户（{@code userId} 业务键已由领域工厂生成）
     * @return 落库后的用户（含技术主键）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(User user) {
        if (user.getNickname() == null || user.getNickname().isBlank()) {
            user.setNickname(DEFAULT_NICKNAME_PREFIX + user.getUserId());
        }
        User saved = userRepository.insert(user);
        roleRepository.insertUserRole(saved.getId(), DEFAULT_ROLE_ID);
        return saved;
    }
}
