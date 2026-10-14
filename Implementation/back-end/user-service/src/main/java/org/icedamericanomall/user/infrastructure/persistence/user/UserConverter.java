package org.icedamericanomall.user.infrastructure.persistence.user;

import org.icedamericanomall.user.domain.model.user.User;
import org.icedamericanomall.user.domain.enums.GenderEnum;
import org.icedamericanomall.user.domain.enums.MemberLevelEnum;
import org.icedamericanomall.user.domain.enums.UserStatusEnum;
import org.icedamericanomall.common.model.EncryptedIdentifier;
import org.icedamericanomall.common.utils.IdGenerator;

/**
 * {@code UserPO} ↔ 领域对象 {@link User} 转换器（基础设施层内部，纯静态转换，不使用反射）。
 *
 * <p>职责边界：只做两侧字段的显式映射（含 {@code Integer ⇄ 领域枚举} 与
 * 手机号/邮箱三字段（cipher + hash + mask）⇄ {@link EncryptedIdentifier}），
 * 不含任何业务判断 —— 状态迁移、换绑校验等规则在领域模型内。
 *
 * <p>V5.2：{@code username} 列 ⇄ 领域 {@code nickname}（术语统一，列名保留为历史兼容）；
 * PO 不映射 password / wx_openid / role_type / 明文 phone（代码停止读写，ADR-017）。
 */
public final class UserConverter {

    private UserConverter() {
    }

    /**
     * PO → 领域对象。
     *
     * @param dataObject 持久化对象（可为 null）
     * @return 领域对象（输入为 null 时返回 null）
     */
    public static User toDomain(UserPO dataObject) {
        if (dataObject == null) {
            return null;
        }
        return User.builder()
                .id(dataObject.getId())
                .userId(IdGenerator.toHexString(dataObject.getUserId()))
                .nickname(dataObject.getUsername())
                .avatar(dataObject.getAvatar())
                .gender(GenderEnum.fromCode(dataObject.getGender()))
                .birthday(dataObject.getBirthday())
                .phone(toIdentifier(dataObject.getPhoneCipher(), dataObject.getPhoneHash(), dataObject.getPhoneMask()))
                .email(toIdentifier(dataObject.getEmailCipher(), dataObject.getEmailHash(), dataObject.getEmailMask()))
                .memberLevel(MemberLevelEnum.fromCode(dataObject.getMemberLevel()))
                .status(UserStatusEnum.fromCode(dataObject.getStatus()))
                .createTime(dataObject.getCreateTime())
                .updateTime(dataObject.getUpdateTime())
                .build();
    }

    /**
     * 领域对象 → PO。
     *
     * @param user 领域对象（可为 null）
     * @return 持久化对象（输入为 null 时返回 null）
     */
    public static UserPO toDataObject(User user) {
        if (user == null) {
            return null;
        }
        UserPO dataObject = new UserPO();
        dataObject.setId(user.getId());
        dataObject.setUserId(IdGenerator.toBytes(user.getUserId()));
        dataObject.setUsername(user.getNickname());
        dataObject.setAvatar(user.getAvatar());
        dataObject.setGender(user.getGender() == null ? null : user.getGender().getCode());
        dataObject.setBirthday(user.getBirthday());
        dataObject.setMemberLevel(user.getMemberLevel() == null ? null : user.getMemberLevel().getCode());
        dataObject.setStatus(user.getStatus() == null ? null : user.getStatus().getCode());
        dataObject.setCreateTime(user.getCreateTime());
        dataObject.setUpdateTime(user.getUpdateTime());

        // PII 三字段拆列（cipher + hash + mask）
        if (user.getPhone() != null) {
            dataObject.setPhoneCipher(user.getPhone().getCipher());
            dataObject.setPhoneHash(user.getPhone().getHash());
            dataObject.setPhoneMask(user.getPhone().getMask());
        }
        if (user.getEmail() != null) {
            dataObject.setEmailCipher(user.getEmail().getCipher());
            dataObject.setEmailHash(user.getEmail().getHash());
            dataObject.setEmailMask(user.getEmail().getMask());
        }
        return dataObject;
    }

    /** 三字段任一存在即还原值对象（掩码/密文缺省时不参与查询语义）。 */
    private static EncryptedIdentifier toIdentifier(String cipher, String hash, String mask) {
        if (cipher == null && hash == null && mask == null) {
            return null;
        }
        return EncryptedIdentifier.fromStorage(cipher, hash, mask);
    }
}
