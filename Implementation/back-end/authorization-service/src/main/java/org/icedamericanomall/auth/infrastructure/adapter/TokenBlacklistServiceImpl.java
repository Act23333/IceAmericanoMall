package org.icedamericanomall.auth.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.app.port.TokenBlacklistService;
import org.icedamericanomall.auth.infrastructure.constant.RedisKeyConstants;
import org.icedamericanomall.auth.infrastructure.utils.JwtUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 访问令牌吊销实现（基础设施层）—— 写 Redis 吊销名单。
 *
 * <p><b>契约键</b>：{@code auth:blacklist:{jti}} 是网关依赖的<b>跨服务契约键</b>
 * （与 {@code user:info:} / {@code user:perms:} 同级，见 ADR-027）；读侧为 gateway-service 的
 * {@code JwtBlacklistFilter}（非阻塞、fail-open）。值不参与判定 —— 网关按"键存在即已吊销"判断。
 *
 * <p><b>TTL 语义</b>：{@code TTL = max(access token 剩余有效期, }{@value #MIN_TTL_SECONDS}s{@code )}。
 * 吊销名单只需覆盖令牌自身的剩余生命期：令牌一旦自然过期，网关的签名 + 时效校验自会拒绝，
 * 黑名单键随之消失，无需长期驻留（避免黑名单无限膨胀）。
 *
 * <p><b>可用性</b>：写入失败仅 WARN，不阻断登出 / 改密主流程（与读侧 fail-open 对称）。
 * 降级窗口内该令牌仍受"同端 refresh 会话 jti 比对"兜底约束（见
 * {@code LoginTokenServiceImpl#isAccessTokenValid}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    /** 黑名单键的值（仅作占位；网关只判断键是否存在）。 */
    private static final String REVOKED_VALUE = "1";

    /** TTL 下限（秒）：避免剩余有效期取整为 0 时写入被立即清除，使吊销形同虚设。 */
    private static final long MIN_TTL_SECONDS = 1L;

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtils jwtUtils;

    @Override
    public void blacklist(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            log.debug("[auth][jwt-blacklist] 请求未携带 access token，跳过拉黑");
            return;
        }
        String jti = jwtUtils.getJti(accessToken);
        if (jti == null || jti.isBlank()) {
            log.debug("[auth][jwt-blacklist] access token 缺少 jti，无法比对吊销名单，跳过拉黑");
            return;
        }

        long ttlSeconds = Math.max(jwtUtils.getRemainingTtl(accessToken), MIN_TTL_SECONDS);
        try {
            stringRedisTemplate.opsForValue().set(RedisKeyConstants.JWT_BLACKLIST_PREFIX + jti,
                    REVOKED_VALUE, ttlSeconds, TimeUnit.SECONDS);
            log.info("[auth][jwt-blacklist] access token 已拉黑: jti={}, ttl={}s", jti, ttlSeconds);
        } catch (Exception e) {
            // 有意 fail-open：吊销是"登录态失效的加速器"，不得让缓存故障阻断用户登出/改密
            log.warn("[auth][jwt-blacklist] 黑名单写入失败，未阻断主流程(fail-open): jti={}, error={}",
                    jti, e.toString());
        }
    }
}
