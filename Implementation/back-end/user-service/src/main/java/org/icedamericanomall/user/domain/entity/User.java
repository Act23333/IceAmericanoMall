// domain/user/model/User.java
package org.icedamericanomall.user.domain.entity;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.model.EncryptedIdentifier;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户资料实体（聚合根）。
 */
@Data
@Builder
public class User {

    private Long id;
    private Long userId;
    private String nickname;
    private String avatar;
    private Integer gender;
    private LocalDate birthday;

    /**
     * 手机号（加密值对象）
     */
    private EncryptedIdentifier phone;

    /**
     * 邮箱（加密值对象）
     */
    private EncryptedIdentifier email;

    private String memberLevel;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ========== 业务方法 ==========

    public void updateNickname(String nickname) {
        this.nickname = nickname;
        this.updateTime = LocalDateTime.now();
    }

    public void updateAvatar(String avatar) {
        this.avatar = avatar;
        this.updateTime = LocalDateTime.now();
    }

    public void bindPhone(String plainPhone) {
        this.phone = EncryptedIdentifier.fromPlain(plainPhone);
        this.updateTime = LocalDateTime.now();
    }

    public void bindEmail(String plainEmail) {
        this.email = EncryptedIdentifier.fromPlain(plainEmail);
        this.updateTime = LocalDateTime.now();
    }

    public void upgradeMemberLevel(String level) {
        this.memberLevel = level;
        this.updateTime = LocalDateTime.now();
    }

    public static User create(String identifier, IdentityTypeEnum identityTypeEnum) {
        EncryptedIdentifier encryptedIdentifier = EncryptedIdentifier.fromPlain(identifier);

        return switch (identityTypeEnum) {
            case PHONE -> User.builder()
                    .phone(encryptedIdentifier)
                    .build();
            case EMAIL -> User.builder()
                    .email(encryptedIdentifier)
                    .build();
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "不支持的登录方式");
        };

    }
}