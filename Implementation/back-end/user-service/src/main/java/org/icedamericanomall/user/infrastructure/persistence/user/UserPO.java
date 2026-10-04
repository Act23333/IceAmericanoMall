package org.icedamericanomall.user.infrastructure.persistence.user;

import org.icedamericanomall.user.domain.enums.UserStatusEnum;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@code user} 表映射对象（PO）—— 仅存在于基础设施持久化层，不外泄（V5.2 资料/凭证分离）。
 *
 * <p>映射范围 = 资料字段 + PII 三字段（cipher/hash/mask）+ 状态/余额/审计；
 * {@code password} / {@code wx_openid} / {@code role_type} / 明文 {@code phone} /
 * {@code last_login_time} 等旧列<b>不再映射</b>（代码停止读写，列注释标 deprecated，
 * 观察期后随迁移删除，ADR-017）。
 *
 * <p>注意：{@code register_time} 列 NOT NULL 且无默认值，仍需映射并在 INSERT 时填充
 * （{@link org.icedamericanomall.user.infrastructure.config.MyMetaObjectHandler}），
 * 其业务语义（注册时间）已由 {@code create_time} 承担，领域层不再读取该列。
 *
 * <p>{@code status} 以原始类型（Integer）映射，领域枚举的转换在 {@link UserConverter}
 * 完成，避免 ORM 注解污染领域枚举。
 */
@Data
@TableName("user")
public class UserPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务唯一键（UUID，32 位） */
    private String userId;

    /** 昵称（历史列名 username，V5.2 领域术语统一为 nickname） */
    private String username;

    private String avatar;

    /** 性别：0=未知，1=男，2=女 */
    private Integer gender;

    private LocalDate birthday;

    /** 会员等级（NORMAL / SILVER / GOLD / PLATINUM） */
    private String memberLevel;

    /** 手机号密文（AES-256-GCM） */
    private String phoneCipher;

    /** 手机号盲索引（HMAC-SHA256，uk_phone_hash） */
    private String phoneHash;

    /** 手机号脱敏展示值 */
    private String phoneMask;

    /** 邮箱密文（AES-256-GCM） */
    private String emailCipher;

    /** 邮箱盲索引（HMAC-SHA256，uk_email_hash） */
    private String emailHash;

    /** 邮箱脱敏展示值 */
    private String emailMask;

    /** 注册时间（审计列，非空；INSERT 时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime registerTime;

    /** 状态：1=正常，0=禁用（UserStatusEnum.code） */
    private Integer status;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
