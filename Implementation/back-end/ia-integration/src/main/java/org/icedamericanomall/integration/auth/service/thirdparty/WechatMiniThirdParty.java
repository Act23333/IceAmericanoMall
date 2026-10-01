package org.icedamericanomall.integration.auth.service.thirdparty;


import org.icedamericanomall.integration.auth.model.WechatMiniLoginReq;
import org.icedamericanomall.integration.auth.model.WechatMiniLoginResp;

public interface WechatMiniThirdParty {
        WechatMiniLoginResp getJsCode2session(WechatMiniLoginReq wechatMiniLoginReq);
}
