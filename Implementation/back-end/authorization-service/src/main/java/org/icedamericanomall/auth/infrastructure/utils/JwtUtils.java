package org.icedamericanomall.auth.infrastructure.utils;

/**
 * @ClassName: JwtUtils
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/4/6 2:05
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.utils
 */
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.infrastructure.config.JwtProperties;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final JwtProperties jwtProperties;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public String generateToken(Map<String, Object> claims) {
        try {
            JWSSigner signer = new RSASSASigner(privateKey);
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
            for (Map.Entry<String, Object> entry : claims.entrySet()) {
                builder.claim(entry.getKey(), entry.getValue());
            }
            Instant now = Instant.now();
            builder.issueTime(Date.from(now));
            builder.expirationTime(Date.from(now.plus(Duration.ofSeconds(jwtProperties.getAccessTokenTtl()))));
            builder.jwtID(UUID.randomUUID().toString().replace("-", "")); // ⭐ 新增
            SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), builder.build());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            log.error("生成 JWT 失败", e);
            throw new org.icedamericanomall.common.exception.BizException(
                    org.icedamericanomall.common.enums.ErrorCode.INTERNAL_ERROR, "JWT 生成失败");
        }
    }

    /** 验证 token 签名并返回解析后的 SignedJWT（供 checkSession 等使用）。 */
    public SignedJWT verifyToken(String tokenString) throws Exception {
        SignedJWT jwt = SignedJWT.parse(tokenString);
        JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) publicKey);
        if (!jwt.verify(verifier)) throw new BizException(ErrorCode.UNAUTHORIZED, "JWT 签名验证失败");
        Date exp = jwt.getJWTClaimsSet().getExpirationTime();
        if (exp != null && exp.before(new Date())) throw new BizException(ErrorCode.TOKEN_EXPIRED, "JWT 已过期");
        return jwt;
    }
    /**
     * 获取 JWT 的 jti（JWT ID）。
     * 用于黑名单 key，避免用整个 token 字符串（节省 Redis 内存）。
     */
    public String getJti(String tokenString) {
        try {
            SignedJWT jwt = SignedJWT.parse(tokenString);
            return jwt.getJWTClaimsSet().getJWTID();
        } catch (Exception e) {
            log.warn("解析 JWT jti 失败", e);
            return null;
        }
    }
    /**
     * 获取 JWT 剩余有效时间（单位：秒）。
     * 用于黑名单 TTL，保证黑名单和 token 同时过期。
     *
     * @return 剩余秒数；已过期或解析失败返回 0
     */
    public long getRemainingTtl(String tokenString) {
        try {
            SignedJWT jwt = SignedJWT.parse(tokenString);
            Date exp = jwt.getJWTClaimsSet().getExpirationTime();
            if (exp == null) {
                return 0;
            }
            long remainingMillis = exp.getTime() - System.currentTimeMillis();
            return remainingMillis > 0 ? remainingMillis / 1000 : 0;
        } catch (Exception e) {
            log.warn("解析 JWT 剩余时间失败", e);
            return 0;
        }
    }

    public long getTtl(String tokenString) {
        return jwtProperties.getAccessTokenTtl();
    }
}
