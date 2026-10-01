package org.icedamericanomall.dto;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.IdentityTypeEnum;

/**
 * 用户"查找或创建"入参（{@code /internal/user/getOrCreate} 契约，ADR-006 幂等端点）。
 *
 * <p>调用方：authorization-service 的 {@code UserAuthProvisioner}（凭证建档时回查/建档资料行）。
 * 幂等口径（ADR-017）：PHONE / EMAIL 按盲索引短路；其余标识类型 user 表无定位列，
 * 幂等由调用方凭证唯一键（user_auth）保证。
 *
 * <p>V5.2 增量字段 {@code nickname}（可选）：新建档时作为昵称写入（注册用户名 / 微信昵称），
 * 为空时由 user-service 生成默认昵称；已存在用户不覆盖。
 */
@Builder
@Data
public class UserAuthCO {

    /** 登录标识（手机号 / 邮箱 / openid 明文） */
    private String identifier;

    /** 标识类型 */
    private IdentityTypeEnum identityType;

    /** 昵称（可选，仅新建档生效） */
    private String nickname;
}
