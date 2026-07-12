package org.noLazy.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SM2 配置（hex 格式公私钥），仅 sm2.enabled=true 时读取。
 */
@Data
@Component
@ConfigurationProperties(prefix = "sm2")
public class Sm2Properties {
    /** 是否激活 SM2 加密传输。 */
    private boolean enabled = false;
    /** hex 格式私钥（留空则随机生成，仅用于 demo）。 */
    private String privateKey;
    /** hex 格式公钥。 */
    private String publicKey;
}
