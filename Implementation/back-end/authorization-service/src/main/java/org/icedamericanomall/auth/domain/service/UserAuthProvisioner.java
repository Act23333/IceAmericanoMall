package org.icedamericanomall.auth.domain.service;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.exception.DuplicateIdentityException;
import org.icedamericanomall.auth.domain.gateway.UserAccountGateway;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.port.DistributedLock;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.DBException;
import org.icedamericanomall.common.model.EncryptedIdentifier;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 认证凭证注册领域服务 —— 跨聚合的"查找或创建"规则（凭证唯一所有权方，ADR-017）。
 *
 * <p>解决的问题：手机号 / 邮箱（敏感标识）与微信 openid（非敏感标识）在首次登录/注册时
 * 都需要"先查、没有再建"，且必须防止并发重复创建。该规则跨
 * {@link UserAuthRepository} 与 {@link UserAccountGateway} 两个协作方，
 * 因此收敛为领域服务，避免在多个登录策略里重复实现。
 *
 * <p>纯业务逻辑：不依赖任何技术组件，互斥能力与远程能力均通过端口注入。
 *
 * <p><b>V5.2 关键收敛</b>：注册链路（{@link #findByHashOrCreateWithCredential}）在
 * 本地凭证行写入 {@code credential}（密码哈希）—— 修复"注册只落 user.password、
 * user_auth 无凭证 → 密码登录必然失败"的凭证脑裂；已存在凭证的标识视为"已注册"，
 * 拒绝覆盖（防账号接管）。
 */
@RequiredArgsConstructor
public class UserAuthProvisioner {

    /** 业务键前缀：用户认证凭证创建锁 */
    private static final String LOCK_KEY_PREFIX = "user_auth:create:";

    private final UserAuthRepository userAuthRepository;
    private final UserAccountGateway userAccountGateway;
    private final DistributedLock distributedLock;

    /**
     * 敏感标识（手机号 / 邮箱）：按盲索引查找，不存在则创建（无凭证，验证码登录口径）。
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
                        .orElseGet(() -> createSensitive(type, identifierHash, rawIdentifier, null, null))));
    }

    /**
     * 敏感标识 + 登录凭证（注册）：按盲索引查找后建档或补齐密码凭证。
     *
     * <p>语义（ADR-017 边界收敛后注册链路）：
     * <ol>
     *   <li>凭证已存在且带 credential → 视为已注册，抛 {@code PHONE_ALREADY_REGISTERED}
     *       （拒绝用注册接口覆盖既有密码，防账号接管）；</li>
     *   <li>凭证已存在但无 credential（先短信登录后注册）→ 补齐密码（手机号控制权已由
     *       上游短信验证码校验证明）；</li>
     *   <li>不存在 → user-service 建档（幂等 + 新用户建档奖励）→ 本地插入带凭证的行。</li>
     * </ol>
     *
     * @param type              标识类型（当前注册链路为 PHONE）
     * @param identifierHash    HMAC 盲索引
     * @param rawIdentifier     明文标识（建档 + 加密用）
     * @param encodedCredential 密码凭证（调用方已哈希）
     * @param nickname          昵称（可选，透传 user-service 建档）
     * @return 已存在的或新建的凭证（含 credential）
     */
    public UserAuth findByHashOrCreateWithCredential(IdentityTypeEnum type, String identifierHash,
                                                     String rawIdentifier, String encodedCredential,
                                                     String nickname) {
        Optional<UserAuth> existing = userAuthRepository.findByIdentityHash(type, identifierHash);
        if (existing.isPresent()) {
            return attachCredentialOrReject(existing.get(), encodedCredential);
        }
        return distributedLock.executeWithLock(
                LOCK_KEY_PREFIX + type.getCode() + ":" + identifierHash,
                () -> userAuthRepository.findByIdentityHash(type, identifierHash)
                        .map(found -> attachCredentialOrReject(found, encodedCredential))
                        .orElseGet(() -> createSensitive(type, identifierHash, rawIdentifier,
                                encodedCredential, nickname)));
    }

    /**
     * 非敏感标识（微信 openid）：按明文查找，不存在则创建。
     *
     * @param type            标识类型（WECHAT / WECHAT_MINI）
     * @param identifierPlain 明文标识（openid）
     * @param unionId         微信 UnionID（可为空）
     * @return 已存在的或新建的凭证
     */
    public UserAuth findByPlainOrCreate(IdentityTypeEnum type, String identifierPlain, String unionId) {
        return findByPlainOrCreate(type, identifierPlain, unionId, null);
    }

    /**
     * 非敏感标识（微信 openid）：按明文查找，不存在则创建（可携带昵称建档）。
     *
     * @param type            标识类型（WECHAT / WECHAT_MINI）
     * @param identifierPlain 明文标识（openid）
     * @param unionId         微信 UnionID（可为空）
     * @param nickname        昵称（可选；仅新建档透传 user-service）
     * @return 已存在的或新建的凭证
     */
    public UserAuth findByPlainOrCreate(IdentityTypeEnum type, String identifierPlain, String unionId,
                                        String nickname) {
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
                        .orElseGet(() -> createPlain(type, identifierPlain, unionId, nickname))));
    }

    // ==================== 内部方法 ====================

    /**
     * 补齐凭证（原行无 credential）或拒绝（已有凭证 = 已注册）。
     *
     * @throws BizException 标识已注册（PHONE_ALREADY_REGISTERED）
     */
    private UserAuth attachCredentialOrReject(UserAuth existing, String encodedCredential) {
        if (existing.getCredential() != null && !existing.getCredential().isBlank()) {
            throw new BizException(ErrorCode.PHONE_ALREADY_REGISTERED, "该手机号已注册");
        }
        existing.updatePassword(encodedCredential);
        userAuthRepository.updateByUserIdAndType(existing);
        return existing;
    }

    /** 创建敏感标识凭证；并发下唯一键冲突则回查（幂等） */
    private UserAuth createSensitive(IdentityTypeEnum type, String identifierHash, String rawIdentifier,
                                     String encodedCredential, String nickname) {
        Long userId = userAccountGateway.getOrCreateUserId(rawIdentifier, type, nickname);
        UserAuth userAuth = UserAuth.builder()
                .userId(userId)
                .identityType(type)
                .identifier(EncryptedIdentifier.fromPlain(rawIdentifier))
                .credential(encodedCredential)
                .lastLoginTime(LocalDateTime.now())
                .build();
        return insertIdempotently(userAuth, () -> userAuthRepository.findByIdentityHash(type, identifierHash),
                encodedCredential);
    }

    /** 创建非敏感标识凭证；并发下唯一键冲突则回查（幂等） */
    private UserAuth createPlain(IdentityTypeEnum type, String identifierPlain, String unionId, String nickname) {
        Long userId = userAccountGateway.getOrCreateUserId(identifierPlain, type, nickname);
        UserAuth userAuth = UserAuth.builder()
                .userId(userId)
                .identityType(type)
                .identifierPlain(identifierPlain)
                .unionId(unionId)
                .lastLoginTime(LocalDateTime.now())
                .build();
        return insertIdempotently(userAuth, () -> userAuthRepository.findByIdentity(type, identifierPlain), null);
    }

    /**
     * 插入；并发冲突时回查既有行（幂等兜底）。
     *
     * @param reload            冲突后回查
     * @param encodedCredential 本次携带的凭证（冲突行已有凭证时按"已注册"拒绝）
     */
    private UserAuth insertIdempotently(UserAuth userAuth,
                                        java.util.function.Supplier<Optional<UserAuth>> reload,
                                        String encodedCredential) {
        try {
            return userAuthRepository.insert(userAuth);
        } catch (DuplicateIdentityException e) {
            // 并发创建：另一请求已插入，回查返回既有记录
            UserAuth reloaded = reload.get().orElseThrow(() -> new DBException(
                    ErrorCode.INTERNAL_ERROR, "登录异常，请重试"));
            if (encodedCredential != null) {
                return attachCredentialOrReject(reloaded, encodedCredential);
            }
            return reloaded;
        }
    }
}
