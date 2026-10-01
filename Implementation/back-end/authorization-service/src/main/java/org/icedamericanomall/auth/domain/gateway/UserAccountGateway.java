package org.icedamericanomall.auth.domain.gateway;

import org.icedamericanomall.common.enums.IdentityTypeEnum;

/**
 * 用户账号上下文网关（防腐层：调用 user-service）。
 *
 * <p>领域层只声明"按登录标识取本地用户 ID（不存在则创建）"这一能力，
 * 参数与返回值均为本上下文的语义，Feign DTO 的转换由基础设施层完成。
 */
public interface UserAccountGateway {

    /**
     * 根据登录标识获取用户 ID，不存在则创建。
     * <p>必须幂等：相同标识多次调用返回同一个 userId。
     *
     * @param identifier   登录标识（手机号 / 邮箱 / 微信 openid）
     * @param identityType 标识类型
     * @return user-service 的用户 ID
     */
    Long getOrCreateUserId(String identifier, IdentityTypeEnum identityType);
}
