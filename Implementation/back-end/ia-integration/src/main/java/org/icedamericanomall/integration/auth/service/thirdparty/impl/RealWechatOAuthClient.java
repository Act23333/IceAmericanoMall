package org.icedamericanomall.integration.auth.service.thirdparty.impl;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.integration.auth.constants.WechatApiUrlConstant;
import org.icedamericanomall.integration.auth.model.WechatOAuthResp;
import org.icedamericanomall.integration.auth.config.WechatOAuthProperties;
import org.icedamericanomall.integration.auth.service.thirdparty.WechatOAuthThirdParty;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 微信 OAuth 真实客户端 —— 仅在配置 {@code wechat.oauth.app-id} 时激活。
 * 调用微信开放平台 sns/oauth2 接口完成 code→openid，并可选拉取用户昵称/头像。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "wechat.oauth.enabled", havingValue = "true")
@EnableConfigurationProperties(WechatOAuthProperties.class)
public class RealWechatOAuthClient implements WechatOAuthThirdParty {

    private final WechatOAuthProperties properties;
    private final RestClient restClient;

    public RealWechatOAuthClient(WechatOAuthProperties properties) {
        this.properties = properties;
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public WechatOAuthResp getUserInfoByCode(String code) {
        Map<?, ?> token = restClient.get()
                .uri(WechatApiUrlConstant.OAUTH_ACCESS_TOKEN_URL
                        + "?appid={a}&secret={s}&code={c}&grant_type=authorization_code",
                        properties.getAppId(), properties.getAppSecret(), code)
                .retrieve()
                .body(Map.class);
        if (token == null || token.get("openid") == null) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "微信授权失败：code 无效或已过期");
        }
        WechatOAuthResp info = new WechatOAuthResp();
        info.setOpenid(String.valueOf(token.get("openid")));
        fillUserInfo(info, String.valueOf(token.get("access_token")));
        return info;
    }

    private void fillUserInfo(WechatOAuthResp info, String accessToken) {
        try {
            Map<?, ?> u = restClient.get()
                    .uri(WechatApiUrlConstant.OAUTH_USER_INFO_URL + "?access_token={t}&openid={o}&lang=zh_CN",
                            accessToken, info.getOpenid())
                    .retrieve().body(Map.class);
            if (u != null) {
                if (u.get("nickname") != null) info.setNickname(String.valueOf(u.get("nickname")));
                if (u.get("headimgurl") != null) info.setAvatarUrl(String.valueOf(u.get("headimgurl")));
            }
        } catch (Exception e) {
            log.warn("获取微信用户信息失败（不影响登录）: {}", e.getMessage());
        }
    }
}
