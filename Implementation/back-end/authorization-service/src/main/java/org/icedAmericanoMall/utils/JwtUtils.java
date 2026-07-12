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
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

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

    /** 验证 token 签名并返回解析后的 SignedJWT（供 checkSession 等使用）。 */
    public SignedJWT verifyToken(String tokenString) throws Exception {
        SignedJWT jwt = SignedJWT.parse(tokenString);
        JWSVerifier verifier = new RSASSAVerifier((java.security.interfaces.RSAPublicKey) publicKey);
        if (!jwt.verify(verifier)) throw new RuntimeException("JWT 签名验证失败");
        Date exp = jwt.getJWTClaimsSet().getExpirationTime();
        if (exp != null && exp.before(new Date())) throw new RuntimeException("JWT 已过期");
        return jwt;
    }
}
