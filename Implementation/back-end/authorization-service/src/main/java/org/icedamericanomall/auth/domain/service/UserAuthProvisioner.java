package org.icedamericanomall.auth.domain.service;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.exception.DuplicateIdentityException;
import org.icedamericanomall.auth.domain.gateway.UserAccountGateway;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.port.DistributedLock;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.DBException;
import org.icedamericanomall.common.model.EncryptedIdentifier;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 认证凭证注册领域服务 —— 跨聚合的"查找或创建"规则。
 *
 * <p>解决的问题：手机号 / 邮箱（敏感标识）与微信 openid（非敏感标识）在首次登录时
 * 都需要"先查、没有再建"，且必须防止并发重复创建。该规则跨
 * {@link UserAuthRepository} 与 {@link UserAccountGateway} 两个协作方，
 * 因此收敛为领域服务，避免在多个登录策略里重复实现。
 *
 * <p>纯业务逻辑：不依赖任何技术组件，互斥能力与远程能力均通过端口注入。
 */
@RequiredArgsConstructor
public class UserAuthProvisioner {

    /** 业务键前缀：用户认证凭证创建锁 */
    private static final String LOCK_KEY_PREFIX = "user_auth:create:";

    private final UserAuthRepository userAuthRepository;
    private final UserAccountGateway userAccountGateway;
    private final DistributedLock distributedLock;

    /**
     * 敏感标识（手机号 / 邮箱）：按盲索引查找，不存在则创建。
     *
     * @param type          标识类型（PHONE / EMAIL）
     * @param identifierHash HMAC 盲索引
     * @param rawIdentifier 明文标识（仅用于创建时加密）
     * @return 已存在的或新建的凭证
     */
    public UserAuth findByHashOrCreate(IdentityTypeEnum type, String identifierHash, String rawIdentifier) {
        Optional<UserAuth> existing = userAuthRepository.findByIdentityHash(type, identifierHash);
        return existing.orElseGet(() -> distributedLock.executeWithLock(
                LOCK_KEY_PREFIX + type.getCode() + ":" + identifierHash,
                () -> userAuthRepository.findByIdentityHash(type, identifierHash)
                        .orElseGet(() -> createSensitive(type, identifierHash, rawIdentifier))));
    }

    /**
     * 非敏感标识（微信 openid）：按明文查找，不存在则创建。
     *
     * @param type            标识类型（WECHAT_MINI）
     * @param identifierPlain 明文标识（openid）
     * @param unionId         微信 UnionID（可为空）
     * @return 已存在的或新建的凭证
     */
    public UserAuth findByPlainOrCreate(IdentityTypeEnum type, String identifierPlain, String unionId) {
        if (unionId != null && !unionId.isBlank()) {
            Optional<UserAuth> byUnionId = userAuthRepository.findFirstByUnionId(unionId);
            if (byUnionId.isPresent()) {
                return byUnionId.get();
            }
        }
        Optional<UserAuth> byPlain = userAuthRepository.findByIdentity(type, identifierPlain);
        return byPlain.orElseGet(() -> distributedLock.executeWithLock(
                LOCK_KEY_PREFIX + type.getCode() + ":" + identifierPlain,
                () -> userAuthRepository.findByIdentity(type, identifierPlain)
                        .orElseGet(() -> createPlain(type, identifierPlain, unionId))));
    }

    /** 创建敏感标识凭证；并发下唯一键冲突则回查（幂等） */
    private UserAuth createSensitive(IdentityTypeEnum type, String identifierHash, String rawIdentifier) {
        Long userId = userAccountGateway.getOrCreateUserId(rawIdentifier, type);
        UserAuth userAuth = UserAuth.builder()
                .userId(userId)
                .identityType(type)
                .identifier(EncryptedIdentifier.fromPlain(rawIdentifier))
                .lastLoginTime(LocalDateTime.now())
                .build();
        return insertIdempotently(userAuth, () -> userAuthRepository.findByIdentityHash(type, identifierHash));
    }

    /** 创建非敏感标识凭证；并发下唯一键冲突则回查（幂等） */
    private UserAuth createPlain(IdentityTypeEnum type, String identifierPlain, String unionId) {
        Long userId = userAccountGateway.getOrCreateUserId(identifierPlain, type);
        UserAuth userAuth = UserAuth.builder()
                .userId(userId)
                .identityType(type)
                .identifierPlain(identifierPlain)
                .unionId(unionId)
                .lastLoginTime(LocalDateTime.now())
                .build();
        return insertIdempotently(userAuth, () -> userAuthRepository.findByIdentity(type, identifierPlain));
    }

    private UserAuth insertIdempotently(UserAuth userAuth, java.util.function.Supplier<Optional<UserAuth>> reload) {
        try {
            return userAuthRepository.insert(userAuth);
        } catch (DuplicateIdentityException e) {
            // 并发创建：另一请求已插入，回查返回既有记录
            return reload.get().orElseThrow(() -> new DBException(
                    ErrorCode.INTERNAL_ERROR, "登录异常，请重试"));
        }
    }
}
