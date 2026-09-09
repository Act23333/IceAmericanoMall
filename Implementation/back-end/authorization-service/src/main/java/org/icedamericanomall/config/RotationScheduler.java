package org.icedamericanomall.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JWT 密钥轮换调度器 —— 仅当 {@code jwt.rotation.enabled=true} 激活。
 *
 * <p>轮换周期可配（默认每天凌晨 2 点），轮换时：
 * <ol>
 *   <li>将 {@code nextKey} 切换为 {@code currentKey}（签发用新 key）</li>
 *   <li>旧 currentKey 保留在 JWKS 中继续验签 24h</li>
 *   <li>JwkSetController 通过 {@code isRotationActive} 判断是否暴露双 key</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jwt.rotation.enabled", havingValue = "true")
public class RotationScheduler {

    private final JwtProperties properties;
    private final JwkSourceConfig jwkSourceConfig;
    @Qualifier("jwkSet") private final JWKSet jwkSet;

    private final AtomicBoolean isRotationActive = new AtomicBoolean(false);

    @Scheduled(cron = "${jwt.rotation.cron:0 0 2 * * ?}")
    public void rotate() {
        log.info("JWT 密钥轮换触发...");
        try {
            RSAKey newKey = jwkSourceConfig.loadNextKey();
            if (newKey == null) {
                log.warn("未配置 next keystore，跳过轮换");
                return;
            }
            RSAKey oldKey = jwkSourceConfig.swapCurrentKey(newKey);
            // 保留旧 key 在 JWKS 中（24h 验签窗口），同时暴露新 key
            jwkSet.getKeys().clear();
            jwkSet.getKeys().add(newKey.toPublicJWK());
            if (oldKey != null) jwkSet.getKeys().add(oldKey.toPublicJWK());
            isRotationActive.set(true);
            log.info("JWT 密钥轮换完成: 新 kid={}, 旧 kid={}（保留验签 24h）",
                    newKey.getKeyID(), oldKey != null ? oldKey.getKeyID() : "N/A");
        } catch (Exception e) {
            log.error("JWT 密钥轮换失败", e);
        }
    }

    public boolean isRotationActive() { return isRotationActive.get(); }
}
