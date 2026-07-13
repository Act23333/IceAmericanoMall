package org.icedAmericanoMall.integration.wechat;

import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 微信 OAuth Mock 客户端 —— 未配置 {@code wechat.oauth.app-id} 时默认激活（本地/未接入微信开放平台）。
 * 用 code 生成确定性虚拟 openid（同一 code 恒定映射到同一用户），便于联调与自动注册。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "wechat.oauth.enabled", havingValue = "false", matchIfMissing = true)
public class MockWechatOAuthClient implements WechatOAuthClient {

    @Override
    public WechatUserInfo getUserInfoByCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "微信授权 code 不能为空");
        }
        String openid = "mock_openid_" + Math.abs(code.hashCode());
        WechatUserInfo info = new WechatUserInfo();
        info.setOpenid(openid);
        info.setNickname("微信用户_" + openid.substring(openid.length() - Math.min(6, openid.length())));
        info.setAvatarUrl("https://mock-wx.example.com/avatar/" + openid + ".png");
        log.info("[MockWechatOAuth] code={} -> openid={}（虚拟数据，未接入微信开放平台）", code, openid);
        return info;
    }
}
