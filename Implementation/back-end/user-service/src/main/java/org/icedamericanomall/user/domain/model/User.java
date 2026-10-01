package org.icedamericanomall.user.domain.model;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.domain.port.PasswordVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户聚合根（纯 POJO，零框架依赖）。
 *
 * <p>双主键语义（ADR-002 / ADR-006）：
 * <ul>
 *   <li>{@link #id} —— 技术主键（BIGINT），集群内部跨服务口径（JWT claim / 网关 header / 各业务表 user_id）</li>
 *   <li>{@link #userId} —— 业务唯一键（UUID），对外暴露与 user-service 域内定位</li>
 * </ul>
 *
 * <p>边界说明：登录标识（手机号 / 邮箱 / 微信）的凭证化存储属于 authorization-service 的
 * {@code user_auth} 聚合（ADR-008）；本聚合保留 {@code phone} / {@code wxOpenid} 两个
 * user 表历史登录列与 {@code password} 凭证哈希（注册、密码登录、找回密码链路沿用）。
 *
 * <p>业务规则内聚在实体方法中（状态迁移、凭证校验、余额不变量），不泄漏到应用层。
 */
@Data
@Builder
public class User {

    /** 技术主键（跨服务 user_id 语义，ADR-006） */
    private Long id;

    /** 业务唯一键（UUID，32 位无连字符） */
    private String userId;

    private String username;

    /** 手机号（历史登录列；邮箱-only 用户为空） */
    private String phone;

    /** 登录凭证哈希（BCrypt）；微信-only / 纯验证码用户为空 */
    private String password;

    private String avatar;

    /** 微信 openid（历史登录列） */
    private String wxOpenid;

    private UserStatusEnum status;

    /** 余额（单位：分，INTEGER —— 禁用 float/double/BigDecimal） */
    private Integer balance;

    /** 角色类型：0=普通用户，1=商家，2=管理员（兼容旧字段；RBAC 以 sys_user_role 为准） */
    private Integer roleType;

    private LocalDateTime registerTime;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 创建新用户资料（未落库状态，{@code id} / {@code userId} 由仓储落库时补齐）。
     *
     * <p>登录标识归属：PHONE 写入 {@code phone} 列（uk_phone 兜底幂等）；
     * 其余标识类型（EMAIL / WECHAT…）在本聚合不落列 —— 其身份由 user_auth 承载。
     *
     * @param identityType 登录标识类型
     * @param identifier   登录标识（手机号 / 邮箱 / openid，明文）
     * @return 新用户聚合
     */
    public static User create(IdentityTypeEnum identityType, String identifier) {
        UserBuilder builder = User.builder()
                .userId(newBusinessId())
                .status(UserStatusEnum.NORMAL)
                .balance(0)
                .roleType(0)
                .registerTime(LocalDateTime.now());
        return switch (identityType) {
            case PHONE -> builder.phone(identifier).build();
            case WECHAT, WECHAT_MINI -> builder.wxOpenid(identifier).build();
            default -> builder.build();
        };
    }

    /** 生成业务唯一键（UUID 去连字符，32 位）。 */
    public static String newBusinessId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ==================== 业务方法 ====================

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

    /**
     * 校验明文密码。
     *
     * @param rawPassword      用户提交的明文密码
     * @param passwordVerifier 哈希比较能力（基础设施用 BCrypt 实现）
     * @return true 表示密码正确
     */
    public boolean verifyPassword(String rawPassword, PasswordVerifier passwordVerifier) {
        if (password == null || password.isBlank()) {
            return false;
        }
        return passwordVerifier.matches(rawPassword, password);
    }

    /** 更新凭证哈希（注册 / 找回密码传入已哈希值）。 */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.updateTime = LocalDateTime.now();
    }

    /** 修改昵称。 */
    public void changeNickname(String nickname) {
        this.username = nickname;
        this.updateTime = LocalDateTime.now();
    }

    /** 修改头像。 */
    public void changeAvatar(String avatarUrl) {
        this.avatar = avatarUrl;
        this.updateTime = LocalDateTime.now();
    }

    /** 设置角色类型（管理后台；0=USER, 1=SELLER, 2=ADMIN）。 */
    public void changeRoleType(int roleType) {
        this.roleType = roleType;
        this.updateTime = LocalDateTime.now();
    }

    /** 记录一次成功登录。 */
    public void markLoginSuccess() {
        this.lastLoginTime = LocalDateTime.now();
        this.updateTime = this.lastLoginTime;
    }

    /**
     * 断言余额充足（内存校验；真正的原子扣减由仓储的条件 UPDATE 承担）。
     *
     * @param amount 扣减金额（分）
     */
    public void assertBalanceSufficient(int amount) {
        if (amount <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "扣减金额必须大于0");
        }
        if (balance == null || balance < amount) {
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "余额不足");
        }
    }
}
