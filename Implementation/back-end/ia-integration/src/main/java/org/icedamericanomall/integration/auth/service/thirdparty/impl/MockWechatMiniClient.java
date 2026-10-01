package org.icedamericanomall.integration.auth.service.thirdparty.impl;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.integration.auth.model.WechatMiniLoginReq;
import org.icedamericanomall.integration.auth.model.WechatMiniLoginResp;
import org.icedamericanomall.integration.auth.service.thirdparty.WechatMiniThirdParty;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 微信小程序 Mock 客户端 —— 未配置 {@code wechat.mini.enabled=true} 时默认激活
 * （本地联调 / 未接入微信开放平台）。
 *
 * <p>用 code 生成确定性虚拟 openid / unionid（同一 code 恒定映射到同一用户），
 * 便于跑通"小程序登录 → 本地建号 → 签发令牌"链路；
 * 与 {@link MockWechatOAuthClient}（网页授权 Mock）保持对称。
 *
 * <p>去掉 {@code @Primary}（V5.2）：与 {@code RealWechatMiniClient} 的条件互斥，
 * {@code @Primary} 无作用却会让「互斥失效」这一异常场景静默降级到 Mock（假登录成功）。
 * 去掉后该场景直接启动失败，与 {@code MockEmailClient} 的既有约定一致。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "wechat.mini.enabled", havingValue = "false", matchIfMissing = true)
public class MockWechatMiniClient implements WechatMiniThirdParty {

    private static final String MOCK_OPENID_PREFIX = "mock_openid_";
    private static final String MOCK_UNIONID_PREFIX = "mock_unionid_";

    @Override
    public WechatMiniLoginResp getJsCode2session(WechatMiniLoginReq wechatMiniLoginReq) {
        if (wechatMiniLoginReq == null || !StringUtils.hasText(wechatMiniLoginReq.getCode())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "微信授权 code 不能为空");
        }
        String code = wechatMiniLoginReq.getCode();
        String openid = MOCK_OPENID_PREFIX + Math.abs(code.hashCode());
        String unionid = MOCK_UNIONID_PREFIX + Math.abs(code.hashCode());
        log.info("[MockWechatMini] code={} -> openid={}（虚拟数据，未接入微信开放平台）", code, openid);
        return WechatMiniLoginResp.builder()
                .openid(openid)
                .unionid(unionid)
                .build();
    }
}
