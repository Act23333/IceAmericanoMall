package org.icedamericanomall.auth.infrastructure.persistence.userauth;

import org.icedamericanomall.auth.domain.enums.UserAuthStatusEnum;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.model.EncryptedIdentifier;

/**
 * DO ↔ 领域对象转换器（基础设施层内部，纯静态转换，不使用反射）。
 */
public final class UserAuthConverter {

    private UserAuthConverter() {
    }

    /**
     * DO → 领域对象。
     *
     * @param dataObject 持久化对象（可为 null）
     * @return 领域对象（输入为 null 时返回 null）
     */
    public static UserAuth toDomain(UserAuthDO dataObject) {
        if (dataObject == null) {
            return null;
        }
        UserAuth userAuth = UserAuth.builder()
                .id(dataObject.getId())
                .userId(dataObject.getUserId())
                .identityType(toIdentityType(dataObject.getIdentityType()))
                .identifierPlain(dataObject.getIdentifierPlain())
                .unionId(dataObject.getUnionId())
                .credential(dataObject.getCredential())
                .status(UserAuthStatusEnum.fromCode(dataObject.getStatus()))
                .createTime(dataObject.getCreateTime())
                .updateTime(dataObject.getUpdateTime())
                .lastLoginTime(dataObject.getLastLoginTime())
                .build();

        // 敏感标识用值对象封装（三字段：cipher + hash + mask）
        if (dataObject.getIdentifierHash() != null) {
            userAuth.setIdentifier(EncryptedIdentifier.fromStorage(
                    dataObject.getIdentifierCipher(),
                    dataObject.getIdentifierHash(),
                    dataObject.getIdentifierMask()));
        }
        return userAuth;
    }

    /**
     * 领域对象 → DO。
     *
     * @param userAuth 领域对象（可为 null）
     * @return 持久化对象（输入为 null 时返回 null）
     */
    public static UserAuthDO toDataObject(UserAuth userAuth) {
        if (userAuth == null) {
            return null;
        }
        UserAuthDO dataObject = new UserAuthDO();
        dataObject.setId(userAuth.getId());
        dataObject.setUserId(userAuth.getUserId());
        dataObject.setIdentityType(userAuth.getIdentityType() == null
                ? null : userAuth.getIdentityType().name());
        dataObject.setIdentifierPlain(userAuth.getIdentifierPlain());
        dataObject.setUnionId(userAuth.getUnionId());
        dataObject.setCredential(userAuth.getCredential());
        dataObject.setStatus(userAuth.getStatus() == null ? null : userAuth.getStatus().getCode());
        dataObject.setLastLoginTime(userAuth.getLastLoginTime());
        dataObject.setCreateTime(userAuth.getCreateTime());
        dataObject.setUpdateTime(userAuth.getUpdateTime());

        // 敏感标识拆三列
        if (userAuth.getIdentifier() != null) {
            dataObject.setIdentifierCipher(userAuth.getIdentifier().getCipher());
            dataObject.setIdentifierHash(userAuth.getIdentifier().getHash());
            dataObject.setIdentifierMask(userAuth.getIdentifier().getMask());
        }
        return dataObject;
    }

    /**
     * 库表存的是 {@link IdentityTypeEnum#name()}；遇到脏数据转成业务异常，
     * 避免 bare {@link IllegalArgumentException} 泄漏到上层。
     */
    private static IdentityTypeEnum toIdentityType(String name) {
        if (name == null) {
            return null;
        }
        try {
            return IdentityTypeEnum.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "非法的认证标识类型: " + name);
        }
    }
}
