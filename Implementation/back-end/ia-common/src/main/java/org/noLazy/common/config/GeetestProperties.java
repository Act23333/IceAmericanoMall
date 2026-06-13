package org.noLazy.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @ClassName: GeetestProperties
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/27 14:17
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.config
 */
@Data
@ConfigurationProperties(prefix = "geetest")
@Component
public class GeetestProperties {
    /**
     * 极验的id
     */
    private String captchaId;

    /**
     * 极验的key
     */
    private String key;
    /** 二次校验接口地址（默认即可） */
    private String validateUrl = "https://gcaptcha4.geetest.com/validate";

}
