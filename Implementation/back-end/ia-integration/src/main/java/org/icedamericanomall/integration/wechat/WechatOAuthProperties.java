package org.icedamericanomall.integration.wechat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信 OAuth 配置。留空 app-id 时走 Mock 客户端（返回虚拟 openid）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat.oauth")
public class WechatOAuthProperties {
    /** 是否启用真实微信客户端；false（默认）走 Mock。 */
    private boolean enabled = false;
    private String appId;
    private String appSecret;
    /** code 换 access_token+openid 接口。 */
    private String accessTokenUrl = "https://api.weixin.qq.com/sns/oauth2/access_token";
    /** 拉取用户信息接口。 */
    private String userInfoUrl = "https://api.weixin.qq.com/sns/userinfo";
}
