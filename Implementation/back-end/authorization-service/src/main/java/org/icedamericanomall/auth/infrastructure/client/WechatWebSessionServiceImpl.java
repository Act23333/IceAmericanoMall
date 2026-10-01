package org.icedamericanomall.auth.infrastructure.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.domain.model.WechatSession;
import org.icedamericanomall.auth.domain.port.WechatWebSessionService;
import org.icedamericanomall.integration.auth.model.WechatOAuthResp;
import org.icedamericanomall.integration.auth.service.thirdparty.WechatOAuthThirdParty;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * 微信网页会话换取实现（基础设施层）—— 对接微信开放平台（
 * {@code wechat.oauth.enabled=true} 走真实客户端，否则走 Mock）。
 *
 * <p>把第三方模型 {@link WechatOAuthResp} 转换成本上下文的 {@link WechatSession}；
 * 与 {@link WechatMiniSessionServiceImpl} 同构（V5.2 收归本地，不再经 user-service 中转）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatWebSessionServiceImpl implements WechatWebSessionService {

    private final WechatOAuthThirdParty wechatOAuthThirdParty;

    @Override
    public WechatSession exchange(String code, String state) {
        WechatOAuthResp response = wechatOAuthThirdParty.getUserInfoByCode(code);
        if (response == null) {
            throw new BizException(ErrorCode.WECHAT_AUTH_FAILED, "微信授权失败");
        }
        log.info("[auth][wechat-web][exchange] openid={}, statePresent={}",
                response.getOpenid() == null ? null : maskOpenid(response.getOpenid()), state != null);
        return new WechatSession(response.getOpenid(), null, response.getPhone(), response.getNickname());
    }

    /** openid 日志脱敏（保留前 6 位便于定位）。 */
    private static String maskOpenid(String openid) {
        return openid.length() <= 6 ? "***" : openid.substring(0, 6) + "***";
    }
}
