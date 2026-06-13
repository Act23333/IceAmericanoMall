package org.icedAmericanoMall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    /** 签名方式：hs256 或 rs256，默认 hs256 */
    private String algorithm = "hs256";
    /** HS256 密钥（至少32字符） */
    private String secret;
    /** RS256 私钥（PEM 格式或路径） */
    private String privateKey;
    /** RS256 公钥（PEM 格式或路径） */
    private String publicKey;
    /** Access Token 有效期（秒） */
    private Long expiration = 3600L;
}