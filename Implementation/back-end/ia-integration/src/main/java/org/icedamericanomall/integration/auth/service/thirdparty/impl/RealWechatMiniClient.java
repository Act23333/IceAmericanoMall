package org.icedamericanomall.integration.auth.service.thirdparty.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.integration.auth.config.WechatMiniProperties;
import org.icedamericanomall.integration.auth.constants.WechatApiUrlConstant;
import org.icedamericanomall.integration.auth.model.WechatMiniLoginReq;
import org.icedamericanomall.integration.auth.model.WechatMiniLoginResp;
import org.icedamericanomall.integration.auth.service.thirdparty.WechatMiniThirdParty;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.integration.WechatApiException;
import org.icedamericanomall.common.utils.HttpUtils;
import org.icedamericanomall.common.utils.JsonUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName: WechatMiniAppClinet
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/9/10 00:15
 * @Version: 1.0.0
 * @ProjectName: back-end
 * @Package: org.icedamericanomall.integration.auth.service.thirdparty.impl
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "wechat.mini.enabled", havingValue = "true")
@EnableConfigurationProperties(WechatMiniProperties.class)
public class RealWechatMiniClient implements WechatMiniThirdParty {
    private WechatMiniProperties wechatMiniProperties;
    private JsonUtils jsonUtils;

    @Override
    public WechatMiniLoginResp getJsCode2session(WechatMiniLoginReq wechatMiniLoginReq) {
        String openId = this.getOpenId(wechatMiniLoginReq.getCode());
        String phone = null;
        if (StringUtils.hasText(wechatMiniLoginReq.getPhoneCode())) {
            phone = this.getPhone(wechatMiniLoginReq.getPhoneCode());
        }
        return WechatMiniLoginResp.builder().openid(openId).phone(phone).build();
    }

    private String getOpenId(String code) {
        Map<String, Object> params = new HashMap<>();
        params.put("appid", wechatMiniProperties.getAppId());
        params.put("secret", wechatMiniProperties.getAppSecret());
        params.put("js_code", code);
        String res = HttpUtils.get(WechatApiUrlConstant.MINI_CODE2SESSION_URL, null, params);
        JsonNode jsonNode = jsonUtils.readTree(res);
        if (jsonNode.has("errcode"))
            throw new WechatApiException(ErrorCode.WECHAT_OPENID_NOT_FOUND, "微信用户openId获取异常：" + jsonNode.get("errmsg").asText());
        return jsonNode.get("openid").asText();
    }

    private String getAccessToken() {
        Map<String, Object> params = new HashMap<>();
        params.put("appid", wechatMiniProperties.getAppId());
        params.put("secret", wechatMiniProperties.getAppSecret());
        String res = HttpUtils.get(WechatApiUrlConstant.MINI_ACCESS_TOKEN_URL, null, params);
        JsonNode jsonNode = jsonUtils.readTree(res);
        if (jsonNode.has("errcode"))
            throw new WechatApiException(ErrorCode.WECHAT_OPENID_NOT_FOUND, "微信用户access_token获取异常：" + jsonNode.get("errmsg").asText());
        return jsonNode.get("access_token").asText();
    }

    private String getPhone(String phoneCode) {
        String accessToken = getAccessToken();
        String url = WechatApiUrlConstant.MINI_PHONE_REQUEST_URL + accessToken;
        Map<String, Object> params = new HashMap<>();
        params.put("code", phoneCode);
        String res = HttpUtils.postForm(url, params);
        JsonNode jsonNode = jsonUtils.readTree(res);
        if (jsonNode.has("errcode"))
            throw new WechatApiException(ErrorCode.WECHAT_OPENID_NOT_FOUND, "微信用户手机号获取异常：" + jsonNode.get("errmsg").asText());
        return jsonNode.get("phone_info").get("purePhoneNumber").asText();
    }
}
