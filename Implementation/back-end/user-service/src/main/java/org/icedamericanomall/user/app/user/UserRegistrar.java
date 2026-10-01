package org.icedamericanomall.user.app.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.repository.RoleRepository;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户建档（应用层共用组件）—— 注册 / 短信登录 / 微信登录三条链路共享的落库动作：
 * 用户行 + 默认 RBAC 角色（保持原 {@code registerUser} 的原子语义）。
 *
 * <p>事务说明：user 行与 sys_user_role 的写入保持同一本地事务（原实现语义；
 * 事务只覆盖本地库写，不含任何远程调用 —— 外部换取类操作由调用方在事务外完成）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegistrar {

    /** 默认角色 ID —— ROLE_USER */
    private static final Long DEFAULT_ROLE_ID = 1L;

    /** 默认用户名前缀（未提供用户名时生成） */
    private static final String DEFAULT_USERNAME_PREFIX = "ice_";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /**
     * 落库新用户：补齐默认用户名 → 插入 user 行 → 授予默认角色。
     *
     * @param user 待建档用户（{@code userId} 业务键已由领域工厂生成）
     * @return 落库后的用户（含技术主键）
     */
    @Transactional(rollbackFor = Exception.class)
    public User register(User user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(DEFAULT_USERNAME_PREFIX + user.getUserId());
        }
        User saved = userRepository.insert(user);
        roleRepository.insertUserRole(saved.getId(), DEFAULT_ROLE_ID);
        return saved;
    }
}
