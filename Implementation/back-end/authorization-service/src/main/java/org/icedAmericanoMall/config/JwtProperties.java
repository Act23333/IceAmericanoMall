package org.icedAmericanoMall.config;

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
 * @Package: org.icedAmericanoMall.config
 */
@Data
@ConfigurationProperties(prefix = "jwt.keystore")
public class JwtProperties {

    private String keystore;
    private String keystorePassword;
    private String keyPassword;
    private String keyAlias;

}
