package org.icedAmericanoMall.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.utils.KeyStoreUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class JwkSourceConfig {

    private final JwtProperties jwtProperties;
    /** 当前签发的 RSAKey（供 RotationScheduler 轮换引用）。 */
    private final AtomicReference<RSAKey> currentKeyRef = new AtomicReference<>();

    /**
     * 加载私钥和证书，返回数组 [0] = PrivateKey, [1] = X509Certificate
     */
    private Object[] loadKeyAndCertificate() throws Exception {
        JwtProperties.Keystore ks = jwtProperties.getKeystore();
        // 环境变量覆盖（生产安全要求，key/password 不从配置文件显式提交）
        String keyStorePath = envOrDefault("JWT_KEYSTORE_PATH", ks.getKeyStorePath());
        String keyStorePassword = envOrDefault("JWT_KEYSTORE_PASSWORD", ks.getKeystorePassword());
        String keyAlias = ks.getKeyAlias();
        String keyPassword = envOrDefault("JWT_KEY_PASSWORD", ks.getKeyPassword());
        String pwd = (keyPassword == null || keyPassword.isEmpty()) ? keyStorePassword : keyPassword;

        return KeyStoreUtils.loadPrivateKeyAndCertificate(
                keyStorePath, keyStorePassword, keyAlias, pwd);
    }

    private static String envOrDefault(String envKey, String defaultValue) {
        String v = System.getenv(envKey);
        return (v != null && !v.isBlank()) ? v : defaultValue;
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
    public PublicKey jwtPublicKey() throws Exception {
        Object[] keyAndCert = loadKeyAndCertificate();
        return ((X509Certificate) keyAndCert[1]).getPublicKey();
    }

    @Bean
    @Qualifier("jwkSet")
    public JWKSet jwkSet() throws Exception {
        Object[] keyAndCert = loadKeyAndCertificate();
        PrivateKey privateKey = (PrivateKey) keyAndCert[0];
        X509Certificate certificate = (X509Certificate) keyAndCert[1];

        RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey((RSAPrivateKey) privateKey)
                .keyID(getKeyId(certificate))
                .build();
        currentKeyRef.set(rsaKey);

        log.info("JWT RS256 密钥加载成功，kid: {}", rsaKey.getKeyID());
        return new JWKSet(rsaKey);
    }

    /** 从 rotation.next 配置加载 next keystore（供 RotationScheduler 调用）。 */
    public RSAKey loadNextKey() throws Exception {
        JwtProperties.Rotation rot = jwtProperties.getRotation();
        String path = rot.getNextKeyStorePath();
        if (path == null || path.isBlank()) return null;
        String ksPwd = envOrDefault("JWT_KEYSTORE_PASSWORD", rot.getNextKeystorePassword());
        String alias = rot.getNextKeyAlias();
        String keyPwd = envOrDefault("JWT_KEY_PASSWORD",
                rot.getNextKeyPassword() != null ? rot.getNextKeyPassword() : ksPwd);

        Object[] keyAndCert = KeyStoreUtils.loadPrivateKeyAndCertificate(path, ksPwd, alias, keyPwd);
        X509Certificate cert = (X509Certificate) keyAndCert[1];
        return new RSAKey.Builder((RSAPublicKey) cert.getPublicKey())
                .privateKey((RSAPrivateKey) keyAndCert[0])
                .keyID(getKeyId(cert)).build();
    }

    /** 轮换 current key（返回旧 key 供 JWKS 24h 验签窗口）。 */
    public RSAKey swapCurrentKey(RSAKey newKey) {
        RSAKey old = currentKeyRef.getAndSet(newKey);
        if (old != null) log.info("当前签发密钥已切换: oldKid={}, newKid={}", old.getKeyID(), newKey.getKeyID());
        return old;
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(JWKSet jwkSet) {
        // 直接使用已有的 JWKSet 创建 JWKSource，避免重复加载
        return new ImmutableJWKSet<>(jwkSet);
    }

    // 移除 privateKey() 方法，避免私钥被随意注入
}
