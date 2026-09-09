package org.icedamericanomall.integration.captcha;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geetest")
public class GeetestProperties {
    private String captchaId;
    private String key;
    private String validateUrl = "https://gcaptcha4.geetest.com/validate";
}
