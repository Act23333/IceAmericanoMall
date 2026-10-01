package org.icedamericanomall.auth.domain.model;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.auth.domain.enums.UserAuthStatusEnum;
import org.icedamericanomall.auth.domain.port.PasswordVerifier;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.model.EncryptedIdentifier;

import java.time.LocalDateTime;

/**
 * 认证凭证聚合根。
 *
 * <p>每种登录方式（手机号 / 邮箱 / 用户名 / 微信）对应一条记录，通过 {@code userId}
 * 关联 user-service 的用户（聚合之间只通过 ID 引用，不持有对象）。
 *
 * <p>业务规则写在聚合内部，不泄漏到 Service；本类<b>不依赖任何框架</b>
 * （密码哈希比较能力通过 {@link PasswordVerifier} 端口注入）。
 */
@Data
@Builder
public class UserAuth {

    /** 技术主键，仅持久化层使用；领域内一律用 {@link #userId} + {@link #identityType} 定位 */
    private Long id;

    /** 关联 user-service 的用户 ID（跨上下文仅存 ID） */
    private Long userId;

    /** 登录标识类型 */
    private IdentityTypeEnum identityType;

    /** 非敏感标识（微信 openid、用户名），明文存储 */
    private String identifierPlain;

    /** 敏感标识（手机号、邮箱），三字段模型：cipher + hash + mask */
    private EncryptedIdentifier identifier;

    /** 登录凭证（密码哈希；验证码 / 微信登录为空） */
    private String credential;

    /** 微信 UnionID（跨应用打通） */
    private String unionId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Builder.Default
    private UserAuthStatusEnum status = UserAuthStatusEnum.ACTIVE;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    // ========== 业务方法 ==========

    /** 是否为敏感标识类型（手机号 / 邮箱走盲索引查询） */
    public boolean isSensitiveIdentity() {
        return identityType == IdentityTypeEnum.PHONE
                || identityType == IdentityTypeEnum.EMAIL;
    }

    /**
     * 校验明文密码。
     *
     * @param rawPassword      用户提交的明文密码
     * @param passwordVerifier 哈希比较能力（基础设施用 BCrypt 实现）
     * @return true 表示密码正确
     */
    public boolean verifyPassword(String rawPassword, PasswordVerifier passwordVerifier) {
        if (credential == null || credential.isBlank()) {
            return false;
        }
        return passwordVerifier.matches(rawPassword, credential);
    }

    /**
     * 断言账号处于可登录状态，否则抛出业务异常。
     */
    public void assertActive() {
        if (status != UserAuthStatusEnum.ACTIVE) {
            throw new BizException(ErrorCode.USER_STATUS_ABNORMAL, "账号状态异常");
        }
    }

    /** 记录一次成功登录 */
    public void markLoginSuccess() {
        this.lastLoginTime = LocalDateTime.now();
        this.updateTime = this.lastLoginTime;
    }

    /** 更新密码（传入已哈希的凭证） */
    public void updatePassword(String newPasswordHash) {
        this.credential = newPasswordHash;
        this.updateTime = LocalDateTime.now();
    }

    /** 绑定 UnionID */
    public void bindUnionId(String unionId) {
        this.unionId = unionId;
        this.updateTime = LocalDateTime.now();
    }

    /** 解绑：状态置为 UNBOUND（不做物理删除，保留审计） */
    public void unbind() {
        this.status = UserAuthStatusEnum.UNBOUND;
        this.updateTime = LocalDateTime.now();
    }

    /** 获取展示用的标识（敏感标识返回脱敏值） */
    public String getDisplayIdentifier() {
        if (identifier != null) {
            return identifier.getMask();
        }
        return identifierPlain;
    }
}
