package org.icedAmericanoMall.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.utils.KeyStoreUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class JwkSourceConfig {

    private final JwtProperties jwtProperties;

    /**
     * 加载私钥和证书，返回数组 [0] = PrivateKey, [1] = X509Certificate
     */
    private Object[] loadKeyAndCertificate() throws Exception {
        String keyPassword = jwtProperties.getKeyPassword();
        String keyStorePassword = jwtProperties.getKeystorePassword();
        String keyStorePath = jwtProperties.getKeyStorePath();
        String keyAlias = jwtProperties.getKeyAlias();
        String pwd = (keyPassword == null || keyPassword.isEmpty()) ? keyStorePassword : keyPassword;

        return KeyStoreUtils.loadPrivateKeyAndCertificate(
                keyStorePath, keyStorePassword, keyAlias, pwd);
    }

    /**
     * 稳定的 keyID（从证书的 SHA-256 指纹中取前16位或使用配置）
     */
    private String getKeyId(X509Certificate certificate) throws Exception {
        // 方法1：使用证书指纹
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] thumbprint = md.digest(certificate.getEncoded());
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprint).substring(0, 16);
        // 方法2：使用固定字符串（建议从配置文件读取）
        // return jwtProperties.getKeyId();
    }

    @Bean
    public PrivateKey jwtPrivateKey() throws Exception {
        Object[] keyAndCert = loadKeyAndCertificate();
        return (PrivateKey) keyAndCert[0];
    }

    @Bean
    public JWKSet jwkSet() throws Exception {
        Object[] keyAndCert = loadKeyAndCertificate();
        PrivateKey privateKey = (PrivateKey) keyAndCert[0];
        X509Certificate certificate = (X509Certificate) keyAndCert[1];

        RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey((RSAPrivateKey) privateKey)
                .keyID(getKeyId(certificate))
                .build();

        log.info("JWT RS256 密钥加载成功，kid: {}", rsaKey.getKeyID());
        return new JWKSet(rsaKey);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(JWKSet jwkSet) {
        // 直接使用已有的 JWKSet 创建 JWKSource，避免重复加载
        return new ImmutableJWKSet<>(jwkSet);
    }

    // 移除 privateKey() 方法，避免私钥被随意注入
}
