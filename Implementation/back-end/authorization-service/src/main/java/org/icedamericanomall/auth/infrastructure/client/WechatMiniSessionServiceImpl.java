package org.icedamericanomall.auth.infrastructure.client;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.model.WechatSession;
import org.icedamericanomall.auth.domain.port.WechatMiniSessionService;
import org.icedamericanomall.integration.auth.model.WechatMiniLoginReq;
import org.icedamericanomall.integration.auth.model.WechatMiniLoginResp;
import org.icedamericanomall.integration.auth.service.thirdparty.WechatMiniThirdParty;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * 微信小程序会话换取实现（基础设施层）—— 对接微信开放平台（
 * {@code wechat.mini.enabled=true} 走真实客户端，否则走 Mock）。
 *
 * <p>把第三方模型 {@link WechatMiniLoginResp} 转换成本上下文的 {@link WechatSession}。
 */
@Component
@RequiredArgsConstructor
public class WechatMiniSessionServiceImpl implements WechatMiniSessionService {

    private final WechatMiniThirdParty wechatMiniThirdParty;

    @Override
    public WechatSession exchange(String code, String phoneCode) {
        WechatMiniLoginReq request = WechatMiniLoginReq.builder()
                .code(code)
                .phoneCode(phoneCode)
                .build();
        WechatMiniLoginResp response = wechatMiniThirdParty.getJsCode2session(request);
        if (response == null) {
            throw new BizException(ErrorCode.WECHAT_AUTH_FAILED, "微信授权失败");
        }
        return new WechatSession(response.openid(), response.unionid(), response.phone());
    }
}
