package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import org.icedamericanomall.constants.UserStatusEnum;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;

import java.time.LocalDateTime;

/**
 * 用户聚合根 — COLA 充血模型 V5.0
 *
 * <pre>
 * 不变量:
 *   1. balance >= 0（余额不为负）
 *   2. 扣款时 balance >= amount
 *   3. 禁用用户不能登录
 * </pre>
 */
@Getter
@TableName("user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String username;
    private String phone;
    private String password;
    private String avatar;
    private String wxOpenid;
    private String phoneHash;  // V2.5: SHA-256(phone) for privacy-safe lookups
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime registerTime;
    private LocalDateTime lastLoginTime;
    private UserStatusEnum status;
    private Integer balance;
    private Integer roleType;
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ==================== 领域行为 ====================

    /** 余额扣款（内存校验，不碰 DB） */
    public int deductBalance(int amount) {
        if (amount <= 0) throw new BizException(ErrorCode.PARAM_ERROR, "扣款金额必须大于0");
        if (this.balance < amount) throw new BizException(ErrorCode.BALANCE_INSUFFICIENT,
                String.format("余额不足: 需要%d分, 当前%d分", amount, this.balance));
        this.balance -= amount;
        return this.balance;
    }

    /** 余额充值 */
    public int creditBalance(int amount) {
        if (amount <= 0) throw new BizException(ErrorCode.PARAM_ERROR, "充值金额必须大于0");
        this.balance += amount;
        return this.balance;
    }

    /** 禁用用户 */
    public void disable() {
        if (this.status == UserStatusEnum.FROZEN) return;
        this.status = UserStatusEnum.FROZEN;
    }

    /** 启用用户 */
    public void enable() {
        this.status = UserStatusEnum.NORMAL;
    }

    /** 修改头像 */
    public void changeAvatar(String avatarUrl) {
        this.avatar = avatarUrl;
    }

    /** 修改昵称 */
    public void changeNickname(String nickname) {
        this.username = nickname;
    }

    // ==================== 查询方法 ====================

    public boolean isActive()    { return this.status == UserStatusEnum.NORMAL; }
    public boolean isFrozen()    { return this.status == UserStatusEnum.FROZEN; }
    public boolean hasBalance(int amount) { return this.balance >= amount; }
    public boolean isSeller()    { return this.roleType != null && this.roleType == 1; }
    public boolean isAdmin()     { return this.roleType != null && this.roleType == 2; }

    // ==================== 持久层 setter（MyBatis-Plus / BeanUtils 兼容） ====================

    public void setId(Long v) { this.id = v; }
    public void setUserId(String v) { this.userId = v; }
    public void setUsername(String v) { this.username = v; }
    public void setPhone(String v) { this.phone = v; }
    public void setPassword(String v) { this.password = v; }
    public void setAvatar(String v) { this.avatar = v; }
    public void setWxOpenid(String v) { this.wxOpenid = v; }
    public void setPhoneHash(String v) { this.phoneHash = v; }
    public void setRegisterTime(LocalDateTime v) { this.registerTime = v; }
    public void setLastLoginTime(LocalDateTime v) { this.lastLoginTime = v; }
    public void setStatus(UserStatusEnum v) { this.status = v; }
    public void setBalance(Integer v) { this.balance = v; }
    public void setRoleType(Integer v) { this.roleType = v; }
    public void setVersion(Integer v) { this.version = v; }
    public void setCreateTime(LocalDateTime v) { this.createTime = v; }
    public void setUpdateTime(LocalDateTime v) { this.updateTime = v; }
}
