package org.icedamericanomall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @ClassName: JwtProperties
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/4/6 1:55
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.config
 */

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
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