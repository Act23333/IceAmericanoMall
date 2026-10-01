package org.icedamericanomall.auth.domain.gateway;

import org.icedamericanomall.common.enums.IdentityTypeEnum;

/**
 * 用户账号上下文网关（防腐层：调用 user-service）。
 *
 * <p>领域层只声明"按登录标识取本地用户 ID（不存在则创建）"这一能力，
 * 参数与返回值均为本上下文的语义，Feign DTO 的转换由基础设施层完成。
 *
 * <p>V5.2（ADR-017）：user-service 的 {@code /internal/user/getOrCreate} 在<b>新建分支</b>
 * 承担默认昵称、默认角色与新用户建档奖励；本网关可携带昵称（注册用户名 / 微信昵称），
 * 幂等语义不变（已存在的用户不会被覆盖）。
 */
public interface UserAccountGateway {

    /**
     * 根据登录标识获取用户 ID，不存在则创建（幂等）。
     *
     * <p>幂等口径：PHONE / EMAIL 由 user-service 按盲索引短路；其余标识类型
     * （微信类）user 表无定位列，幂等由调用方（{@code user_auth} 唯一键 + 建档锁）保证。
     *
     * @param identifier   登录标识（手机号 / 邮箱 / 微信 openid）
     * @param identityType 标识类型
     * @param nickname     昵称（可选；仅新建档生效，null/空白由 user-service 生成默认昵称）
     * @return user-service 的用户 ID（技术主键，跨服务 user_id 口径）
     */
    Long getOrCreateUserId(String identifier, IdentityTypeEnum identityType, String nickname);
}
