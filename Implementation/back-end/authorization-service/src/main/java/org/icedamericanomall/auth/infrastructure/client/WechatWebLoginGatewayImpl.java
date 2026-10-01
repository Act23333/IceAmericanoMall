package org.icedamericanomall.auth.infrastructure.client;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.gateway.WechatWebLoginGateway;
import org.icedamericanomall.auth.domain.model.UserIdentity;
import org.icedamericanomall.client.AuthClient;
import org.icedamericanomall.dto.LoginRespCO;
import org.icedamericanomall.dto.WechatWebLoginReqCO;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * 微信网页登录网关实现（防腐层）—— Feign 调 user-service。
 *
 * <p>把 user-service 的 {@code LoginRespCO} 转换成本上下文的 {@link UserIdentity}，
 * 隔离外部模型与外部字段变更。
 */
@Component
@RequiredArgsConstructor
public class WechatWebLoginGatewayImpl implements WechatWebLoginGateway {

    private final AuthClient authClient;

    @Override
    public UserIdentity loginByWebCode(String code, String state) {
        WechatWebLoginReqCO request = new WechatWebLoginReqCO();
        request.setCode(code);
        request.setState(state);
        LoginRespCO response = authClient.loginByWechat(request);
        if (response == null || response.getUserId() == null) {
            throw new BizException(ErrorCode.WECHAT_AUTH_FAILED, "微信登录失败");
        }
        return new UserIdentity(parseUserId(response.getUserId()), response.getUsername());
    }

    /**
     * user-service 的 userId 为业务 UUID，本上下文使用技术主键 Long。
     * <p>user-service 返回非数字时降级为 null（由上层判定登录失败），避免抛参数异常。
     */
    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
