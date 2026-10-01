package org.icedamericanomall.user.infrastructure.persistence.user;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code user} 表映射对象（DO）—— 仅存在于基础设施持久化层，不外泄。
 *
 * <p>{@code status} / {@code roleType} 以原始类型（Integer）映射，
 * 领域枚举的转换在 {@link UserConverter} 完成，避免 ORM 注解污染领域枚举。
 */
@Data
@TableName("user")
public class UserDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务唯一键（UUID，32 位） */
    private String userId;

    private String username;

    private String phone;

    /** BCrypt 凭证哈希（微信-only / 纯验证码用户可空） */
    private String password;

    private String avatar;

    /** 微信 openid（历史登录列） */
    private String wxOpenid;

    /** SHA-256(phone) 辅助列（PII 加密激活后启用） */
    private String phoneHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime registerTime;

    private LocalDateTime lastLoginTime;

    /** 状态：1=正常，0=禁用（UserStatusEnum.code） */
    private Integer status;

    /** 余额（单位：分） */
    private Integer balance;

    /** 角色类型：0=普通用户，1=商家，2=管理员 */
    private Integer roleType;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
