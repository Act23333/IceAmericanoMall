package org.icedamericanomall.user.infrastructure.persistence.user;

import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.model.UserStatusEnum;

/**
 * {@code UserDO} ↔ 领域对象 {@link User} 转换器（基础设施层内部，纯静态转换，不使用反射）。
 *
 * <p>职责边界：只做两侧字段的显式映射（含 {@code Integer ⇄ 领域枚举}），
 * 不含任何业务判断 —— 状态迁移等规则在领域模型内。
 */
public final class UserConverter {

    private UserConverter() {
    }

    /**
     * DO → 领域对象。
     *
     * @param dataObject 持久化对象（可为 null）
     * @return 领域对象（输入为 null 时返回 null）
     */
    public static User toDomain(UserDO dataObject) {
        if (dataObject == null) {
            return null;
        }
        return User.builder()
                .id(dataObject.getId())
                .userId(dataObject.getUserId())
                .username(dataObject.getUsername())
                .phone(dataObject.getPhone())
                .password(dataObject.getPassword())
                .avatar(dataObject.getAvatar())
                .wxOpenid(dataObject.getWxOpenid())
                .status(UserStatusEnum.fromCode(dataObject.getStatus()))
                .balance(dataObject.getBalance())
                .roleType(dataObject.getRoleType())
                .registerTime(dataObject.getRegisterTime())
                .lastLoginTime(dataObject.getLastLoginTime())
                .createTime(dataObject.getCreateTime())
                .updateTime(dataObject.getUpdateTime())
                .build();
    }

    /**
     * 领域对象 → DO。
     *
     * @param user 领域对象（可为 null）
     * @return 持久化对象（输入为 null 时返回 null）
     */
    public static UserDO toDataObject(User user) {
        if (user == null) {
            return null;
        }
        UserDO dataObject = new UserDO();
        dataObject.setId(user.getId());
        dataObject.setUserId(user.getUserId());
        dataObject.setUsername(user.getUsername());
        dataObject.setPhone(user.getPhone());
        dataObject.setPassword(user.getPassword());
        dataObject.setAvatar(user.getAvatar());
        dataObject.setWxOpenid(user.getWxOpenid());
        dataObject.setStatus(user.getStatus() == null ? null : user.getStatus().getCode());
        dataObject.setBalance(user.getBalance());
        dataObject.setRoleType(user.getRoleType());
        dataObject.setRegisterTime(user.getRegisterTime());
        dataObject.setLastLoginTime(user.getLastLoginTime());
        dataObject.setCreateTime(user.getCreateTime());
        dataObject.setUpdateTime(user.getUpdateTime());
        return dataObject;
    }
}
