package org.icedamericanomall.auth.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.app.port.TokenVersionService;
import org.icedamericanomall.auth.infrastructure.constant.RedisKeyConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 用户级令牌版本实现（基础设施层）—— 读写 Redis 版本号。
 *
 * <p><b>契约键</b>：{@code auth:tokenver:{userId}} 是网关依赖的<b>跨服务契约键</b>
 * （与 {@code user:info:} / {@code user:perms:} / {@code auth:blacklist:} 同级，见 ADR-031）；
 * 读侧为 gateway-service 的 {@code JwtBlacklistFilter}（非阻塞、fail-open，与 jti 黑名单合并为一次 MGET）。
 * 值必须是可解析的十进制长整型（{@code INCR} 产出），调用方不得手工覆盖该键。
 *
 * <p><b>不设 TTL</b>（与 jti 黑名单的"随令牌过期"语义刻意相反）：版本键一旦过期，
 * 读取方会把版本视为 {@code 0}，此前"因改密/禁用而版本落后"的令牌将被重新放行。
 * 因此版本键常驻（每个被吊销过的用户一个键，体量可控）。
 *
 * <p><b>可用性</b>：读写失败仅 WARN，不阻断调用方主流程（与读侧 fail-open 对称）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenVersionServiceImpl implements TokenVersionService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public long currentVersion(Long userId) {
        if (userId == null) {
            log.warn("[auth][token-version] userId 缺失，按版本 0 处理");
            return 0L;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(versionKey(userId));
            return parseVersion(value, userId);
        } catch (Exception e) {
            // 有意 fail-open：读取失败按"从未吊销"处理，不得让缓存故障阻断签发
            log.warn("[auth][token-version] 版本读取失败，按版本 0 处理(fail-open): userId={}, error={}",
                    userId, e.toString());
            return 0L;
        }
    }

    @Override
    public long incrementVersion(Long userId) {
        if (userId == null) {
            log.warn("[auth][token-version] userId 缺失，跳过版本推进");
            return INCREMENT_FAILED;
        }
        try {
            Long newVersion = stringRedisTemplate.opsForValue().increment(versionKey(userId));
            if (newVersion == null) {
                log.warn("[auth][token-version] 版本推进返回空值，未阻断主流程(fail-open): userId={}", userId);
                return INCREMENT_FAILED;
            }
            log.info("[auth][token-version] 用户级令牌吊销已生效: userId={}, version={}", userId, newVersion);
            return newVersion;
        } catch (Exception e) {
            // 有意 fail-open：吊销是"登录态失效的加速器"，不得让缓存故障阻断改密/禁用主流程
            log.warn("[auth][token-version] 版本推进失败，未阻断主流程(fail-open): userId={}, error={}",
                    userId, e.toString());
            return INCREMENT_FAILED;
        }
    }

    /** 契约键：{@code auth:tokenver:{userId}}（{@link RedisKeyConstants#JWT_TOKEN_VERSION_PREFIX}）。 */
    private String versionKey(Long userId) {
        return RedisKeyConstants.JWT_TOKEN_VERSION_PREFIX + userId;
    }

    /**
     * 解析版本值。
     *
     * @return 键缺失 / 空白 / 不可解析时返回 {@code 0}（与"键缺失 = 版本 0"兼容规则一致）
     */
    private long parseVersion(String value, Long userId) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[auth][token-version] 版本值不可解析，按版本 0 处理: userId={}, value={}", userId, value);
            return 0L;
        }
    }
}
