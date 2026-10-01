package org.icedamericanomall.user.app.user;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.LoginRespCO;
import org.icedamericanomall.dto.PasswordLoginReqCO;
import org.icedamericanomall.dto.RegisterReqCO;
import org.icedamericanomall.dto.ResetPasswordReqCO;
import org.icedamericanomall.dto.SmsLoginReqCO;
import org.icedamericanomall.dto.WechatMiniLoginReqCO;
import org.icedamericanomall.dto.WechatWebLoginReqCO;
import org.icedamericanomall.user.app.points.PointsManager;
import org.icedamericanomall.user.domain.gateway.WechatOAuthGateway;
import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.model.WechatProfile;
import org.icedamericanomall.user.domain.port.PasswordVerifier;
import org.icedamericanomall.user.domain.port.SmsCodeStore;
import org.icedamericanomall.user.domain.repository.RoleRepository;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.icedamericanomall.user.domain.service.SmsCodeVerifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 认证写操作（应用层编排）—— 注册 / 密码登录 / 短信登录 / 微信登录 / 找回密码。
 * 对应 {@code /internal/auth/**}（ia-api {@code AuthClient} 的对端，authorization-service 依赖）。
 *
 * <p>事务边界（ADR-005 / ADR-016）：本类不声明事务；唯一需要原子的"用户行 + 默认角色"
 * 由 {@link UserRegistrar} 的本地事务承担。外部调用（微信 code 换取）刻意置于事务之外，
 * 避免"事务内调外部接口"（原实现在事务内发起微信换取，重构修正）。
 *
 * <p>登录奖励（首次 200 分 / 回归 >30 天 100 分）沿用原语义：发放失败仅告警不阻断登录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuthManager {

    /** 登录奖励：首次登录 */
    private static final int POINTS_FIRST_LOGIN = 200;

    /** 登录奖励：回归登录（距上次登录超过 30 天） */
    private static final int POINTS_RETURN_LOGIN = 100;

    /** 回归判定窗口（天） */
    private static final int RETURN_LOGIN_DAYS = 30;

    /** 积分类型：2=下单（历史沿用值，登录奖励复用） */
    private static final int POINTS_TYPE_LOGIN = 2;

    private static final String BONUS_SOURCE_FIRST = "首次登录奖励";
    private static final String BONUS_SOURCE_RETURN = "回归登录奖励";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRegistrar userRegistrar;
    private final PasswordVerifier passwordVerifier;
    private final SmsCodeVerifier smsCodeVerifier;
    private final SmsCodeStore smsCodeStore;
    private final WechatOAuthGateway wechatOAuthGateway;
    private final PointsManager pointsManager;

    /**
     * 注册（手机号 + 密码 + 短信验证码）。
     *
     * <p>流程：校验验证码 → 唯一性检查 → 建档（user + 默认角色，UserRegistrar 事务）
     * → 消费验证码 → 返回登录信息。
     */
    public LoginRespCO register(RegisterReqCO req) {
        String phone = req.getPhone();
        String username = req.getUsername();
        smsCodeVerifier.verify(phone, req.getCode());
        checkUserUnique(phone, username);

        User user = User.create(IdentityTypeEnum.PHONE, phone);
        user.setUsername(username);
        user.changePassword(passwordVerifier.encode(req.getPassword()));
        User saved = userRegistrar.register(user);

        smsCodeStore.remove(phone);
        return toLoginResp(saved);
    }

    /** 密码登录（用户名或手机号 + 密码）。 */
    public LoginRespCO loginByPassword(PasswordLoginReqCO req) {
        User user = (StrUtil.isBlank(req.getUsername())
                ? userRepository.findByPhone(req.getPhone())
                : userRepository.findByUsername(req.getUsername()))
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        if (!user.verifyPassword(req.getPassword(), passwordVerifier)) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }
        user.assertActive();
        return toLoginResp(user);
    }

    /** 短信登录（手机号 + 验证码；未注册则自动建档）。 */
    public LoginRespCO loginBySms(SmsLoginReqCO req) {
        String phone = req.getPhone();
        smsCodeVerifier.verify(phone, req.getCode());

        User user = userRepository.findByPhone(phone).orElse(null);
        boolean isNew = false;
        if (user == null) {
            user = userRegistrar.register(User.create(IdentityTypeEnum.PHONE, phone));
            isNew = true;
        } else {
            user.assertActive();
        }
        awardLoginBonus(user, isNew);
        return toLoginResp(user);
    }

    /**
     * 微信网页（OAuth2）登录：code → openid，按 openid 查/建用户。
     *
     * <p>外部换取（{@link WechatOAuthGateway}）在事务外执行；建档由 UserRegistrar 事务承担。
     */
    public LoginRespCO loginByWechat(WechatWebLoginReqCO req) {
        WechatProfile profile = wechatOAuthGateway.getUserInfoByCode(req.getCode());
        String openid = profile.openid();

        User user = userRepository.findByWxOpenid(openid).orElse(null);
        boolean isNew = false;
        if (user == null) {
            // 新用户：以微信身份自动注册（无手机号/密码，password 列可空）
            user = User.create(IdentityTypeEnum.WECHAT, openid);
            if (StrUtil.isNotBlank(profile.nickname())) {
                user.setUsername(profile.nickname());
            }
            user.setAvatar(profile.avatarUrl());
            user = userRegistrar.register(user);
            isNew = true;
        } else {
            user.assertActive();
        }
        awardLoginBonus(user, isNew);
        return toLoginResp(user);
    }

    /**
     * 微信小程序登录。
     *
     * <p><b>保留端点半成品</b>：小程序 code→openid 的换取与登录闭环已在
     * authorization-service 实现（{@code WechatMiniLoginStrategy}，完全等价的能力），
     * 本端点仅保留路由兼容（ia-api {@code AuthClient.wechatMiniLogin} 对端），
     * 当前无活跃调用方；如需启用，按 {@link #loginByWechat} 模式接入
     * {@code WechatMiniThirdParty} 即可。
     */
    public LoginRespCO wechatMiniLogin(WechatMiniLoginReqCO req) {
        log.warn("[user][wechat-mini-login][skip] 小程序登录已由 authorization-service 承载，本端点保留未接入");
        return null;
    }

    /** 找回密码：校验短信验证码后重置密码。 */
    public void resetPassword(ResetPasswordReqCO request) {
        String phone = request.getPhone();
        smsCodeVerifier.verify(phone, request.getCode());
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "该手机号未注册"));
        user.changePassword(passwordVerifier.encode(request.getNewPassword()));
        userRepository.update(user);
        smsCodeStore.remove(phone);
    }

    // ==================== 内部方法 ====================

    /** 首次/回归登录奖励（V2.5）：首次 200 分，回归（距上次登录 >30 天）100 分。 */
    private void awardLoginBonus(User user, boolean isNew) {
        LocalDateTime now = LocalDateTime.now();
        int points = 0;
        if (isNew) {
            points = POINTS_FIRST_LOGIN;
        } else if (user.getLastLoginTime() != null
                && user.getLastLoginTime().plusDays(RETURN_LOGIN_DAYS).isBefore(now)) {
            points = POINTS_RETURN_LOGIN;
        }
        if (points > 0) {
            try {
                pointsManager.addPoints(user.getId(), points, POINTS_TYPE_LOGIN,
                        isNew ? BONUS_SOURCE_FIRST : BONUS_SOURCE_RETURN);
            } catch (Exception e) {
                log.warn("登录奖励发放失败: userId={}", user.getId(), e);
            }
        }
        user.markLoginSuccess();
        userRepository.update(user);
    }

    /** 注册唯一性检查（uk_phone / uk_username 业务键）。 */
    private void checkUserUnique(String phone, String username) {
        if (userRepository.findByPhone(phone).isPresent()) {
            throw new BizException(ErrorCode.PHONE_ALREADY_REGISTERED, "手机号已被注册");
        }
        if (StrUtil.isNotBlank(username) && userRepository.findByUsername(username).isPresent()) {
            throw new BizException(ErrorCode.USERNAME_ALREADY_TAKEN, "用户名已被占用");
        }
    }

    /** 构建登录响应 —— 聚合 RBAC 五表模型的角色 + 权限。 */
    private LoginRespCO toLoginResp(User user) {
        LoginRespCO resp = new LoginRespCO();
        resp.setUserId(user.getUserId());
        resp.setUsername(user.getUsername());

        Set<String> roles = roleRepository.findRoleCodesByUserId(user.getId());
        Set<String> perms = new HashSet<>();
        perms.addAll(roleRepository.findDirectPermissionCodesByUserId(user.getId()));
        perms.addAll(roleRepository.findRolePermissionCodesByUserId(user.getId()));
        resp.setRoles(roles);
        resp.setPermissions(perms);
        return resp;
    }
}
