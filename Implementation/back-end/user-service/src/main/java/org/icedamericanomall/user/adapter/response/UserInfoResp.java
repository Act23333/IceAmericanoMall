package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.domain.model.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 用户信息响应 VO — V5.2 资料/凭证分离 + PII 脱敏（原有字段全部保留，新增为增量字段）。
 *
 * <p>契约口径（ADR-017）：
 * <ul>
 *   <li>{@code username} —— 保留原字段名，值来自领域 {@code nickname}（术语统一在领域层）</li>
 *   <li>{@code phone} / {@code email} —— 返回<b>掩码</b>（如 {@code 138****8000}），明密文不出上下文</li>
 *   <li>{@code gender} / {@code birthday} / {@code memberLevel} —— 增量字段（资料重构新增）</li>
 *   <li>{@code roleType} —— 兼容字段，由 RBAC 五表角色编码派生（{@code RoleTypeDerivation}），
 *       不再读 {@code user.role_type} 列</li>
 *   <li>{@code registerTime} —— 回退 {@code create_time}（{@code register_time} 列退出领域读写）</li>
 * </ul>
 * 注意 {@code userId} 是业务 UUID（安全！不暴露自增主键 id）。
 */
@Data
public class UserInfoResp {

    /** 业务唯一ID（安全！不暴露自增主键id） */
    private String userId;

    /** 用户名/昵称 */
    private String username;

    /** 手机号（掩码展示） */
    private String phone;

    /** 邮箱（掩码展示；未绑定为 null） */
    private String email;

    /** 头像 */
    private String avatar;

    /** 性别：0=未知，1=男，2=女 */
    private Integer gender;

    /** 生日 */
    private LocalDate birthday;

    /** 会员等级（NORMAL / SILVER / GOLD / PLATINUM） */
    private String memberLevel;

    /** 用户状态 1-正常 0-禁用 */
    private Integer status;

    /** 注册时间 */
    private LocalDateTime registerTime;

    /** 商城余额（单位：分） */
    private Integer balance;

    /** 角色类型: 0-普通用户, 1-商家, 2-管理员（兼容字段，五表派生） */
    private Integer roleType;

    /** RBAC 角色编码集合 (ROLE_USER/ROLE_SELLER/ROLE_ADMIN) */
    private Set<String> roles;

    /** RBAC 权限编码集合 (user:admin/seller:admin/...) */
    private Set<String> permissions;

    /** 领域模型 → 响应 VO（PII 一律输出掩码）。 */
    public static UserInfoResp from(User user) {
        UserInfoResp resp = new UserInfoResp();
        resp.setUserId(user.getUserId());
        resp.setUsername(user.getNickname());
        resp.setPhone(user.getPhone() == null ? null : user.getPhone().getMask());
        resp.setEmail(user.getEmail() == null ? null : user.getEmail().getMask());
        resp.setAvatar(user.getAvatar());
        resp.setGender(user.getGender());
        resp.setBirthday(user.getBirthday());
        resp.setMemberLevel(user.getMemberLevel());
        resp.setStatus(user.getStatus() == null ? null : user.getStatus().getCode());
        resp.setRegisterTime(user.getCreateTime());
        resp.setBalance(user.getBalance());
        return resp;
    }
}
