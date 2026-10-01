package org.icedamericanomall.integration.auth.model;

import lombok.Builder;

/**
 * @ClassName: WxSessionResponse
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/9/10 00:35
 * @Version: 1.0.0
 * @ProjectName: back-end
 * @Package: org.icedamericanomall.integration.auth.model
 */
@Builder
public record WechatMiniLoginResp(
        String openid,
        String phone,
        String unionid
) {

}
