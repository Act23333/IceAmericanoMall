package org.icedamericanomall.auth.infrastructure.adapter;

import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.app.dto.SessionCheckDTO;
import org.icedamericanomall.auth.app.dto.TokenGenerateParam;
import org.icedamericanomall.auth.app.dto.TokenPair;
import org.icedamericanomall.auth.app.port.LoginTokenService;
import org.icedamericanomall.auth.app.port.TokenVersionService;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.model.RefreshTokenInfo;
import org.icedamericanomall.auth.infrastructure.constant.RedisKeyConstants;
import org.icedamericanomall.auth.infrastructure.config.JwtProperties;
import org.icedamericanomall.auth.infrastructure.model.TokenIssueParam;
import org.icedamericanomall.auth.infrastructure.utils.JwtUtils;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.UnauthorizedException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 登录令牌服务实现（基础设施层）—— JWT 签发 + Redis 会话状态。
 *
 * <p>会话模型：
 * <ul>
 *   <li>会话 Key：{@code login:user:refresh:{userId}:{deviceType}}，同端互斥（覆盖旧会话）</li>
 *   <li>反查索引：{@code login:user:token:key:{refreshToken}} → 会话 Key</li>
 *   <li>accessToken 不落库，靠 jti 与会话中的 jti 比对判断是否被踢</li>
 *   <li>accessToken 携带 {@code ver} claim（签发时刻的用户令牌版本，键缺失 = {@code 0}），
 *       供网关做用户级吊销比对（ADR-031）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginTokenServiceImpl implements LoginTokenService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_DEVICE_ID = "deviceId";
    private static final String CLAIM_DEVICE_TYPE = "deviceType";
    private static final String CLAIM_PLATFORM = "platform";
    private static final long DEFAULT_IDLE_TIMEOUT_MINUTES = 30L;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final TokenVersionService tokenVersionService;

    @Override
    public TokenPair createLoginResponse(TokenGenerateParam tokenGenerateParam) {
        TokenIssueParam issueParam = TokenIssueParam.builder()
                .userId(tokenGenerateParam.userId())
                .deviceId(tokenGenerateParam.deviceId())
                .deviceType(tokenGenerateParam.deviceType())
                .platform(tokenGenerateParam.platform())
                .newLogin(true)
                .build();
        return createSession(issueParam);
    }

    @Override
    public TokenPair createRefreshResponse(String refreshToken) {
        RefreshTokenInfo info = loadSession(refreshToken);
        long remainingMs = info.getMaxExpireAt() - System.currentTimeMillis();
        if (remainingMs <= 0) {
            revokeSession(refreshToken);
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh Token 已过绝对有效期，请重新登录");
        }
        TokenIssueParam issueParam = TokenIssueParam.builder()
                .userId(info.getUserId())
                .deviceId(info.getDeviceId())
                .deviceType(info.getDeviceType())
                .platform(info.getPlatform())
                .newLogin(false)
                .maxExpireAt(info.getMaxExpireAt())
                .build();
        TokenPair tokenPair = createSession(issueParam);
        log.info("轮换 Refresh Token for user {}", info.getUserId());
        return tokenPair;
    }

    @Override
    public SessionCheckDTO checkSession(String accessToken) {
        try {
            // 1. 验签 + 过期校验
            SignedJWT jwt = jwtUtils.verifyToken(accessToken);

            // 2. 校验会话是否被踢（jti 比对）
            Long userId = jwt.getJWTClaimsSet().getLongClaim(CLAIM_USER_ID);
            String deviceType = jwt.getJWTClaimsSet().getStringClaim(CLAIM_DEVICE_TYPE);
            String jti = jwt.getJWTClaimsSet().getJWTID();
            if (!isAccessTokenValid(refreshTokenKey(userId, deviceType), jti)) {
                return new SessionCheckDTO(false, 0L, DEFAULT_IDLE_TIMEOUT_MINUTES);
            }

            // 3. 计算剩余 TTL
            Date exp = jwt.getJWTClaimsSet().getExpirationTime();
            long ttl = Math.max(0L, (exp.getTime() - System.currentTimeMillis()) / 1000);
            return new SessionCheckDTO(true, ttl, DEFAULT_IDLE_TIMEOUT_MINUTES);
        } catch (Exception e) {
            log.debug("会话校验失败: {}", e.getMessage());
            return new SessionCheckDTO(false, 0L, DEFAULT_IDLE_TIMEOUT_MINUTES);
        }
    }

    @Override
    public void logout(String refreshToken) {
        revokeSession(refreshToken);
    }

    // ========== 私有实现 ==========

    /** 校验 refreshToken 有效并加载会话状态 */
    private RefreshTokenInfo loadSession(String refreshToken) {
        String sessionKey = stringRedisTemplate.opsForValue().get(userTokenKey(refreshToken));
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh Token 无效或已过期");
        }
        RefreshTokenInfo info = (RefreshTokenInfo) redisTemplate.opsForValue().get(sessionKey);
        if (info == null) {
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh Token 无效或已过期");
        }
        if (!Objects.equals(info.getRefreshToken(), refreshToken)) {
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh Token 已过期");
        }
        return info;
    }

    /**
     * 会话 Key：{@code login:user:refresh:{userId}:{deviceType}}
     */
    private String refreshTokenKey(Long userId, String deviceType) {
        return new StringJoiner(":")
                .add(RedisKeyConstants.REFRESH_TOKEN_PREFIX)
                .add(String.valueOf(userId))
                .add(deviceType)
                .toString();
    }

    /**
     * 反查索引 Key：{@code login:user:token:key:{refreshToken}}
     */
    private String userTokenKey(String refreshToken) {
        return new StringJoiner(":")
                .add(RedisKeyConstants.REFRESH_TOKEN_USER_PREFIX)
                .add(refreshToken)
                .toString();
    }

    /**
     * 创建会话：生成 accessToken + refreshToken，并写入 Redis。
     *
     * @param issueParam 用户信息 + 设备类型 + 签发场景
     * @return TokenPair（accessToken、refreshToken、accessToken 剩余有效期）
     */
    private TokenPair createSession(TokenIssueParam issueParam) {
        // 1. 生成 accessToken
        String accessToken = jwtUtils.generateToken(buildClaims(issueParam));
        String accessTokenJti = jwtUtils.getJti(accessToken);

        // 2. 生成 refreshToken（把 jti 和绝对过期时间显式写入会话）
        String refreshToken = createRefreshToken(issueParam, accessTokenJti);

        return new TokenPair(issueParam.userId(), accessToken, refreshToken, jwtUtils.getTtl(accessToken));
    }

    /**
     * 写入 refreshToken 与会话。
     *
     * <p>根据 {@code newLogin} 区分两种场景：
     * <ul>
     *   <li>新建登录：maxExpire = now + refreshTokenTtl</li>
     *   <li>轮换刷新：maxExpire 继承原值，防止无限续期</li>
     * </ul>
     *
     * <p>同端互斥：直接覆盖旧 Key，旧 accessToken 因 jti 不匹配而失效。
     */
    private String createRefreshToken(TokenIssueParam issueParam, String accessTokenJti) {
        final long issuedAt = System.currentTimeMillis();
        final long refreshTokenTtlMs = jwtProperties.getRefreshTokenTtl() * 1000L;
        final long maxExpire = issueParam.newLogin()
                ? issuedAt + refreshTokenTtlMs
                : issueParam.maxExpireAt();
        final long ttlSeconds = (maxExpire - issuedAt) / 1000;
        final String refreshToken = UUID.randomUUID().toString().replace("-", "");

        RefreshTokenInfo info = new RefreshTokenInfo(refreshToken, accessTokenJti, issueParam.userId(),
                issueParam.deviceId(), issueParam.deviceType(), issueParam.platform(), issuedAt, maxExpire);

        // 双 key 相同 TTL，物理与业务生命周期一致
        redisTemplate.opsForValue().set(refreshTokenKey(issueParam.userId(), issueParam.deviceType()),
                info, ttlSeconds, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(userTokenKey(refreshToken),
                refreshTokenKey(issueParam.userId(), issueParam.deviceType()), ttlSeconds, TimeUnit.SECONDS);
        log.info("创建 Refresh Token for user {}, TTL={}s", issueParam.userId(), ttlSeconds);
        return refreshToken;
    }

    /**
     * 登出：删除会话与反查索引。
     * <p>删除后：refreshToken 立即失效；accessToken 因 jti 比对失败而失效。
     */
    private void revokeSession(String refreshToken) {
        String indexKey = userTokenKey(refreshToken);
        String sessionKey = stringRedisTemplate.opsForValue().get(indexKey);
        if (sessionKey != null && !sessionKey.isBlank()) {
            redisTemplate.delete(sessionKey);
        }
        stringRedisTemplate.delete(indexKey);
        log.info("登出吊销 Refresh Token — 会话Key={}", sessionKey);
    }

    @Override
    public void revokeAllSessions(Long userId) {
        if (userId == null) {
            log.warn("[auth][revoke-all-sessions] userId 缺失，跳过会话拆除");
            return;
        }
        int removed = 0;
        for (String deviceType : allDeviceTypes()) {
            String sessionKey = refreshTokenKey(userId, deviceType);
            RefreshTokenInfo info = (RefreshTokenInfo) redisTemplate.opsForValue().get(sessionKey);
            if (info == null || info.getRefreshToken() == null || info.getRefreshToken().isBlank()) {
                continue;
            }
            revokeSession(info.getRefreshToken());
            removed++;
        }
        log.info("[auth][revoke-all-sessions] 用户级会话拆除完成: userId={}, removed={}", userId, removed);
    }

    /**
     * 会话端类型取值域：{@link PlatformEnum} 全量 + 无平台时的
     * {@link TokenGenerateParam#UNKNOWN_DEVICE_TYPE} 兜底。
     *
     * <p>会话键按 deviceType 分片，端类型受枚举约束（有界），故按枚举遍历拆除，
     * 避免 {@code KEYS}/{@code SCAN} 这类全库扫描。
     */
    private static List<String> allDeviceTypes() {
        List<String> deviceTypes = new ArrayList<>(PlatformEnum.values().length + 1);
        for (PlatformEnum platform : PlatformEnum.values()) {
            deviceTypes.add(platform.name());
        }
        deviceTypes.add(TokenGenerateParam.UNKNOWN_DEVICE_TYPE);
        return deviceTypes;
    }

    /** accessToken 是否仍为当前会话（jti 未被后续登录覆盖） */
    private boolean isAccessTokenValid(String sessionKey, String jti) {
        RefreshTokenInfo info = (RefreshTokenInfo) redisTemplate.opsForValue().get(sessionKey);
        if (info == null) {
            return false;
        }
        return Objects.equals(jti, info.getAccessTokenJti());
    }

    /**
     * 构建 JWT claims：userId、deviceType、deviceId、platform、ver。
     * <p>jti 由 {@link JwtUtils} 自动加入；权限信息不放 JWT（网关层实时查询）。
     *
     * <p>{@code ver} = 签发时刻该用户的令牌版本（{@code auth:tokenver:{userId}}，键缺失 = {@code 0}），
     * 登录与 refresh 换发走同一链路，故两者都带当前版本（ADR-031）。
     */
    private Map<String, Object> buildClaims(TokenIssueParam issueParam) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, issueParam.userId());
        claims.put(CLAIM_DEVICE_TYPE, issueParam.deviceType());
        claims.put(JwtUtils.TOKEN_VERSION_CLAIM, tokenVersionService.currentVersion(issueParam.userId()));
        if (issueParam.deviceId() != null) {
            claims.put(CLAIM_DEVICE_ID, issueParam.deviceId());
        }
        if (issueParam.platform() != null) {
            claims.put(CLAIM_PLATFORM, issueParam.platform().getCode());
        }
        return claims;
    }
}
