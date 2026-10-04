package org.icedamericanomall.user.domain.service;

import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.user.domain.exception.DuplicateUserException;
import org.icedamericanomall.user.domain.model.user.User;
import org.icedamericanomall.common.lock.DistributedLock;
import org.icedamericanomall.user.domain.port.NewUserRegistrar;
import org.icedamericanomall.user.domain.port.NewUserRewardService;
import org.icedamericanomall.user.domain.repository.UserRepository;

import java.util.Optional;

/**
 * 用户"查找或创建"领域服务 —— 跨聚合（user 仓储 + 建档/奖励能力 + 分布式锁）的规则。
 *
 * <p>解决的问题：authorization-service 注册/登录凭据建档后按业务键回查技术主键
 * （ADR-006 幂等端点 {@code /internal/user/getOrCreate}），需要"先查、没有再建"，
 * 且必须防止并发重复创建。规则跨多个协作方，因此收敛为领域服务。
 *
 * <p>纯业务逻辑：不依赖任何技术组件，互斥/建档/奖励能力均通过端口注入。
 * 幂等三层防线：① 先查短路；② 业务键互斥锁 + 双重检查；③ 唯一键冲突回查兜底。
 *
 * <p><b>V5.2 变更（ADR-017）</b>：建档分支统一（注册 / 短信首登 / 邮箱首登 / 微信登录
 * 共用同一漏斗）—— 默认昵称与默认 RBAC 角色经 {@link NewUserRegistrar} 事务落库，
 * 随后经 {@link NewUserRewardService} 发放"新用户建档奖励"（已存在用户不发）；
 * 昵称可在建档时由调用方提供（注册用户名 / 微信昵称），未提供则回退默认昵称。
 */
public class UserProvisioner {

    /** 业务键前缀：用户创建锁 */
    private static final String LOCK_KEY_PREFIX = "user:create:";

    private final UserRepository userRepository;
    private final DistributedLock distributedLock;
    private final NewUserRegistrar newUserRegistrar;
    private final NewUserRewardService newUserRewardService;

    public UserProvisioner(UserRepository userRepository,
                           DistributedLock distributedLock,
                           NewUserRegistrar newUserRegistrar,
                           NewUserRewardService newUserRewardService) {
        this.userRepository = userRepository;
        this.distributedLock = distributedLock;
        this.newUserRegistrar = newUserRegistrar;
        this.newUserRewardService = newUserRewardService;
    }

    /**
     * 按登录标识查找或创建用户（幂等）。
     *
     * @param identityType 登录标识类型（PHONE / EMAIL 落各自盲索引列；其余类型不落列）
     * @param identifier   登录标识（手机号 / 邮箱 / openid 明文）
     * @param nickname     昵称（可选；仅新建分支使用，null/空白回退默认昵称）
     * @return 已存在或新建的用户聚合（含技术主键，ADR-006 跨服务口径）
     */
    public User findOrCreate(IdentityTypeEnum identityType, String identifier, String nickname) {
        Optional<User> existing = userRepository.findByIdentity(identityType, identifier);
        if (existing.isPresent()) {
            return existing.get();
        }
        return distributedLock.executeWithLock(
                LOCK_KEY_PREFIX + identityType.name() + ":" + identifier,
                () -> userRepository.findByIdentity(identityType, identifier)
                        .orElseGet(() -> createIdempotently(identityType, identifier, nickname)));
    }

    /** 创建（事务建档 + 建档奖励）；并发下唯一键冲突则回查（幂等兜底）。 */
    private User createIdempotently(IdentityTypeEnum identityType, String identifier, String nickname) {
        User newUser = User.create(identityType, identifier);
        if (nickname != null && !nickname.isBlank()) {
            newUser.setNickname(nickname.trim());
        }
        try {
            User created = newUserRegistrar.register(newUser);
            // 建档奖励：仅新建分支触发；失败不阻断建档（实现方捕获告警）
            newUserRewardService.award(created.getId());
            return created;
        } catch (DuplicateUserException e) {
            return userRepository.findByIdentity(identityType, identifier)
                    .orElseThrow(() -> e);
        }
    }
}
