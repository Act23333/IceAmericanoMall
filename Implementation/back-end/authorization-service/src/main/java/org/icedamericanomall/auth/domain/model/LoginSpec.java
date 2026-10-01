package org.icedamericanomall.auth.domain.model;

import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.common.enums.IdentityTypeEnum;

import java.util.Collections;
import java.util.Map;

/**
 * 一次登录尝试的领域入参（不可变值对象）。
 *
 * <p>应用层的 {@code LoginCommand} 只承载用例编排所需信息（人机验证凭证等），
 * 领域层的策略与风控只依赖本对象，避免领域层反向依赖应用层。
 *
 * @param identityType   身份类型（用户名 / 手机号 / 邮箱 / 微信）
 * @param identifier     身份值（用户名、手机号、openid 等）
 * @param credentialType 凭证类型（密码 / 短信验证码 / 微信 code）
 * @param credential     凭证值
 * @param platform       平台类型（服务端识别，不信任前端）
 * @param deviceId       设备唯一标识
 * @param clientIp       客户端 IP
 * @param userAgent      客户端 UA
 * @param extraParams    登录方式特有字段
 */
public record LoginSpec(
        IdentityTypeEnum identityType,
        String identifier,
        CredentialTypeEnum credentialType,
        String credential,
        PlatformEnum platform,
        String deviceId,
        String clientIp,
        String userAgent,
        Map<String, Object> extraParams
) {

    /** 紧凑构造器：防御性拷贝，避免调用方后续修改 */
    public LoginSpec {
        extraParams = extraParams == null
                ? Collections.emptyMap()
                : Map.copyOf(extraParams);
    }

    /** 从扩展字段取值 */
    public String extra(String key) {
        Object value = extraParams.get(key);
        return value == null ? null : value.toString();
    }

    /** 从扩展字段取值（带默认值） */
    public String extraOrDefault(String key, String defaultValue) {
        String value = extra(key);
        return value == null ? defaultValue : value;
    }
}
