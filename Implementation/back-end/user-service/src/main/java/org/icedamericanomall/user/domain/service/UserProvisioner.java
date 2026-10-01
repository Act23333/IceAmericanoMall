package org.icedamericanomall.user.domain.service;

import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.user.domain.exception.DuplicateUserException;
import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.port.DistributedLock;
import org.icedamericanomall.user.domain.repository.UserRepository;

import java.util.Optional;

/**
 * 用户"查找或创建"领域服务 —— 跨聚合（user 仓储 + 分布式锁端口）的规则。
 *
 * <p>解决的问题：authorization-service 注册/登录后按业务键回查技术主键
 * （ADR-006 幂等端点 {@code /internal/user/getOrCreate}），需要"先查、没有再建"，
 * 且必须防止并发重复创建。规则跨 {@link UserRepository} 与 {@link DistributedLock}
 * 两个协作方，因此收敛为领域服务。
 *
 * <p>纯业务逻辑：不依赖任何技术组件，互斥能力通过端口注入。
 * 幂等三层防线：① 先查短路；② 业务键互斥锁 + 双重检查；③ 唯一键冲突回查兜底。
 */
public class UserProvisioner {

    /** 业务键前缀：用户创建锁 */
    private static final String LOCK_KEY_PREFIX = "user:create:";

    private final UserRepository userRepository;
    private final DistributedLock distributedLock;

    public UserProvisioner(UserRepository userRepository, DistributedLock distributedLock) {
        this.userRepository = userRepository;
        this.distributedLock = distributedLock;
    }

    /**
     * 按登录标识查找或创建用户（幂等）。
     *
     * @param identityType 登录标识类型（PHONE 落 uk_phone；其余类型由 user_auth 承载身份）
     * @param identifier   登录标识（明文）
     * @return 已存在或新建的用户聚合（含技术主键，ADR-006 跨服务口径）
     */
    public User findOrCreate(IdentityTypeEnum identityType, String identifier) {
        Optional<User> existing = userRepository.findByIdentity(identityType, identifier);
        if (existing.isPresent()) {
            return existing.get();
        }
        return distributedLock.executeWithLock(
                LOCK_KEY_PREFIX + identityType.name() + ":" + identifier,
                () -> userRepository.findByIdentity(identityType, identifier)
                        .orElseGet(() -> createIdempotently(identityType, identifier)));
    }

    /** 创建；并发下唯一键冲突则回查（幂等兜底） */
    private User createIdempotently(IdentityTypeEnum identityType, String identifier) {
        try {
            return userRepository.insert(User.create(identityType, identifier));
        } catch (DuplicateUserException e) {
            return userRepository.findByIdentity(identityType, identifier)
                    .orElseThrow(() -> e);
        }
    }
}
