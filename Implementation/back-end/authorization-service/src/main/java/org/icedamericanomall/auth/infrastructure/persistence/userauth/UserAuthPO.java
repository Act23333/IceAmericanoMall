package org.icedamericanomall.auth.infrastructure.persistence.userauth;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * user_auth 表映射对象（PO）—— 仅存在于基础设施持久化层，不外泄。
 *
 * <p>敏感标识采用三字段模型：
 * <ul>
 *   <li>{@code identifierPlain}：非敏感标识（openid / 用户名）明文</li>
 *   <li>{@code identifierCipher}：AES-GCM 密文（Base64，VARCHAR）</li>
 *   <li>{@code identifierHash}：HMAC-SHA256 盲索引（查询用，唯一索引一部分）</li>
 *   <li>{@code identifierMask}：脱敏展示值</li>
 * </ul>
 */
@Data
@TableName("user_auth")
public class UserAuthPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 user.id（技术主键，跨服务 user_id 语义） */
    private Long userId;

    /** 标识类型（IdentityTypeEnum.name()） */
    private String identityType;

    /** 非敏感标识明文（openid / 用户名） */
    private String identifierPlain;

    /** 敏感标识密文（AES-GCM，Base64） */
    private String identifierCipher;

    /** 敏感标识盲索引（HMAC-SHA256） */
    private String identifierHash;

    /** 敏感标识脱敏展示值 */
    private String identifierMask;

    /** 微信 UnionID（跨应用打通） */
    private String unionId;

    /** 登录凭证（密码哈希；验证码 / 微信登录为空） */
    private String credential;

    /** 状态（UserAuthStatusEnum.code） */
    private Integer status;

    private LocalDateTime lastLoginTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
