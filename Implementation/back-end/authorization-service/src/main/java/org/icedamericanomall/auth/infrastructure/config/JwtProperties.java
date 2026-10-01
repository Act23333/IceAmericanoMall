package org.icedamericanomall.auth.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置（前缀 {@code jwt}）。
 *
 * <p>Token 有效期也收敛在此：避免在 {@code JwtUtils} / 令牌服务里用
 * {@code @Value} 字段注入（本类由 {@code @EnableConfigurationProperties} 注册为 Bean，
 * 消费方统一构造器注入）。
 */
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** accessToken 有效期（秒） */
    private long accessTokenTtl = 3600;

    /** refreshToken 有效期（秒），同时作为会话的绝对有效期（轮换不延长） */
    private long refreshTokenTtl = 2592000;

    /** Keystore 配置（@ConfigurationProperties 自动注入 sub-key） */
    private Keystore keystore = new Keystore();

    /** 密钥轮换配置 */
    private Rotation rotation = new Rotation();

    @Data
    public static class Keystore {
        private String keyStorePath;
        private String keystorePassword;
        private String keyPassword;
        private String keyAlias;
    }

    @Data
    public static class Rotation {
        /** 轮换 cron（默认每天凌晨2点）。 */
        private String cron = "0 0 2 * * ?";
        /** 是否启用密钥轮换（dev 默认关）。 */
        private boolean enabled = false;
        /** next keystore path（与 current 不同文件）。 */
        private String nextKeyStorePath;
        private String nextKeystorePassword;
        private String nextKeyPassword;
        private String nextKeyAlias;
    }
}