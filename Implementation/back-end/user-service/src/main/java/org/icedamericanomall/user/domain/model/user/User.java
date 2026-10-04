package org.icedamericanomall.user.domain.model.user;

import org.icedamericanomall.user.domain.enums.UserStatusEnum;
import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.model.EncryptedIdentifier;
import org.icedamericanomall.user.domain.enums.GenderEnum;
import org.icedamericanomall.user.domain.enums.MemberLevelEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 用户聚合根（纯 POJO，零框架依赖）—— V5.2 资料/凭证分离后的"资料聚合"。
 *
 * <p>双主键语义（ADR-002 / ADR-006）：
 * <ul>
 *   <li>{@link #id} —— 技术主键（BIGINT），集群内部跨服务口径（JWT claim / 网关 header / 各业务表 user_id）</li>
 *   <li>{@link #userId} —— 业务唯一键（UUID，32 位），对外暴露与 user-service 域内定位</li>
 * </ul>
 *
 * <p><b>边界声明（V5.2 / ADR-017）</b>：本聚合<b>只承载"资料"</b>——昵称、头像、性别、生日、
 * 会员等级、余额、状态，以及两个 PII 标识（手机号 / 邮箱，三字段加密值对象，仅用于展示、
 * 换绑与跨上下文定位）。
 * <ul>
 *   <li>登录<b>凭证</b>（密码哈希、微信 openid）归 authorization-service 的 {@code user_auth} 聚合
 *       （ADR-008）——本聚合已移除 {@code password} / {@code wxOpenid} 字段与
 *       {@code verifyPassword} / {@code changePassword} 行为，"注册即可密码登录"的凭证一致性
 *       由凭证唯一所有权方保证；</li>
 *   <li>角色/权限<b>不落在本聚合</b>（RBAC 五表 sys_role/sys_permission/sys_user_role/
 *       sys_role_permission/sys_user_permission 派生 + Redis 缓存，ADR-017）——
 *       {@code role_type} 列已退役（保留列仅为观察期，代码不读不写）。</li>
 * </ul>
 *
 * <p>业务规则内聚在实体方法中（状态迁移、标识换绑校验、余额不变量），不泄漏到应用层。
 */
@Data
@Builder
public class User {

    /** 手机号格式（中国大陆） */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 邮箱格式（宽松校验：本地部分@域名，域名含点） */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    /** 技术主键（跨服务 user_id 语义，ADR-006） */
    private Long id;

    /** 业务唯一键（UUID，32 位无连字符） */
    private String userId;

    /** 昵称（展示名；持久化映射历史列名 {@code username}） */
    private String nickname;

    private String avatar;

    /** 性别（闭集取值域，持久化/报文口径为 Integer code） */
    private GenderEnum gender;

    private LocalDate birthday;

    /** 手机号（PII 三字段：cipher + hash + mask；未绑定为 null） */
    private EncryptedIdentifier phone;

    /** 邮箱（PII 三字段：cipher + hash + mask；未绑定为 null） */
    private EncryptedIdentifier email;

    /** 会员等级（闭集取值域，持久化/报文口径为 String code） */
    private MemberLevelEnum memberLevel;

    private UserStatusEnum status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 创建新用户资料（未落库状态，{@code id} 由仓储落库时补齐）。
     *
     * <p>标识归属：PHONE / EMAIL 落各自三字段列（盲索引唯一键兜底幂等）；
     * WECHAT / WECHAT_MINI / USERNAME 等其余标识类型在本聚合<b>不落列</b> ——
     * 其身份与凭证由 authorization-service 的 {@code user_auth} 承载（ADR-008/017），
     * 本上下文只按 {@code userId} 关联资料。
     *
     * @param identityType 登录标识类型
     * @param identifier   登录标识（手机号 / 邮箱；其余类型忽略）
     * @return 新用户聚合
     */
    public static User create(IdentityTypeEnum identityType, String identifier) {
        UserBuilder builder = User.builder()
                .userId(newBusinessId())
                .status(UserStatusEnum.NORMAL)
                .gender(GenderEnum.UNKNOWN)
                .memberLevel(MemberLevelEnum.NORMAL);
        if (identityType == null) {
            return builder.build();
        }
        return switch (identityType) {
            case PHONE -> builder.phone(EncryptedIdentifier.fromPlain(identifier)).build();
            case EMAIL -> builder.email(EncryptedIdentifier.fromPlain(identifier)).build();
            default -> builder.build();
        };
    }

    /** 生成业务唯一键（UUID 去连字符，32 位）。 */
    public static String newBusinessId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ==================== 状态机 ====================

    /** 是否被冻结。 */
    public boolean isFrozen() {
        return status == UserStatusEnum.FROZEN;
    }

    /** 断言账号处于可登录 / 可访问状态，否则抛业务异常（禁用用户不能登录）。 */
    public void assertActive() {
        if (status == UserStatusEnum.FROZEN) {
            throw new BizException(ErrorCode.USER_STATUS_ABNORMAL);
        }
    }

    /** 禁用（幂等；状态迁移唯一入口）。 */
    public void disable() {
        if (this.status == UserStatusEnum.FROZEN) {
            return;
        }
        this.status = UserStatusEnum.FROZEN;
        this.updateTime = LocalDateTime.now();
    }

    /** 启用/恢复（状态迁移唯一入口）。 */
    public void enable() {
        this.status = UserStatusEnum.NORMAL;
        this.updateTime = LocalDateTime.now();
    }

    // ==================== 资料变更 ====================

    /** 修改昵称。 */
    public void changeNickname(String nickname) {
        this.nickname = nickname;
        this.updateTime = LocalDateTime.now();
    }

    /** 修改头像。 */
    public void changeAvatar(String avatarUrl) {
        this.avatar = avatarUrl;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 修改性别。
     *
     * @param gender 性别枚举（边界层经 {@code GenderEnum.fromCode} 完成 code 反查与合法性校验）
     * @throws BizException 空值（PARAM_ERROR）
     */
    public void changeGender(GenderEnum gender) {
        if (gender == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "性别不能为空");
        }
        this.gender = gender;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 修改生日（不得晚于今天）。
     *
     * @throws BizException 未来日期（PARAM_ERROR）
     */
    public void changeBirthday(LocalDate birthday) {
        if (birthday != null && birthday.isAfter(LocalDate.now())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "生日不能晚于今天");
        }
        this.birthday = birthday;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 修改会员等级（归一化由 {@code MemberLevelEnum.fromCode} 承担）。
     *
     * @throws BizException 空值（PARAM_ERROR）
     */
    public void changeMemberLevel(MemberLevelEnum memberLevel) {
        if (memberLevel == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "会员等级不能为空");
        }
        this.memberLevel = memberLevel;
        this.updateTime = LocalDateTime.now();
    }

    // ==================== 标识换绑（PII 整体替换） ====================

    /**
     * 换绑手机号 —— 格式校验后整体替换三字段（cipher + hash + mask）。
     *
     * <p>说明：验证码校验属认证边界（authorization-service），换绑入口在调用链上游完成
     * "新手机号可用性 + 验证码"校验后再落到本方法；本方法只承担领域内的格式不变量。
     *
     * @param rawPhone 新手机号明文（中国大陆 11 位）
     * @throws BizException 格式非法（PARAM_ERROR）
     */
    public void changePhone(String rawPhone) {
        String normalized = rawPhone == null ? null : rawPhone.trim();
        if (normalized == null || !PHONE_PATTERN.matcher(normalized).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
        }
        this.phone = EncryptedIdentifier.fromPlain(normalized);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 换绑邮箱 —— 归一化（去空格 + 小写）后整体替换三字段（cipher + hash + mask）。
     *
     * @param rawEmail 新邮箱明文
     * @throws BizException 格式非法（PARAM_ERROR）
     */
    public void changeEmail(String rawEmail) {
        String normalized = rawEmail == null ? null : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (normalized == null || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "邮箱格式不正确");
        }
        this.email = EncryptedIdentifier.fromPlain(normalized);
        this.updateTime = LocalDateTime.now();
    }

    // ==================== 余额不变量 ====================

}
