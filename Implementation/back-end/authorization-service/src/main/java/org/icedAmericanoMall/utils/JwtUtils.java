package org.icedAmericanoMall.utils;

/**
 * @ClassName: JwtUtils
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/4/6 2:05
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedAmericanoMall.utils
 */
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final PrivateKey privateKey;   // 从 JwtConfig 注入

    @Value("${jwt.access-token-ttl:3600}")
    private Long accessTokenTtl;

    public String generateToken(Map<String, Object> claims) {
        try {
            JWSSigner signer = new RSASSASigner(privateKey);
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
            for (Map.Entry<String, Object> entry : claims.entrySet()) {
                builder.claim(entry.getKey(), entry.getValue());
            }
            long now = System.currentTimeMillis();
            builder.issueTime(new Date(now));
            builder.expirationTime(new Date(now + accessTokenTtl * 1000));

            SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), builder.build());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            log.error("生成 JWT 失败", e);
            throw new org.noLazy.common.exception.BizException(
                    org.noLazy.common.enums.ErrorCode.INTERNAL_ERROR, "JWT 生成失败");
        }
    }

    // 如果需要解码和验证，可增加方法，但网关将使用公钥验证，此处不需要
}
