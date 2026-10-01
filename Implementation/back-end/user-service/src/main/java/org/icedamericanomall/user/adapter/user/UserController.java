package org.icedamericanomall.user.adapter.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.user.adapter.request.SmsCodeSendReq;
import org.icedamericanomall.user.adapter.request.UpdateProfileReq;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.user.app.sms.SmsCodeManager;
import org.icedamericanomall.user.app.user.UserInfoManager;
import org.icedamericanomall.user.app.user.UserManager;
import org.icedamericanomall.user.app.user.UserProfileManager;
import org.icedamericanomall.common.annotation.RateLimit;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
import org.icedamericanomall.common.domain.UserInfo;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.RateLimitUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 用户控制器 — V5.0 RBAC 增强（/api/user 契约路径与报文保持不变）。
 *
 * <p>getUserInfo 返回角色+权限供前端 UI 权限判断；发码链路已迁移
 * verification-service（ADR-016），本层注解 {@code @RateLimit} 与多级手机号限流保持原样。
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserManager userManager;
    private final SmsCodeManager smsCodeManager;
    private final UserProfileManager userProfileManager;
    private final UserInfoManager userInfoManager;
    private final RateLimitUtils rateLimitUtils;

    /** 发送短信验证码（人机验证 + 多级限流 + 远端签发投递）。 */
    @PostMapping("/code")
    @RateLimit(key = "ip", limit = 10, duration = 1, unit = TimeUnit.MINUTES)
    public Result<Void> sendCode(@RequestBody SmsCodeSendReq smsCodeSendReq) {
        if (!rateLimitUtils.checkSmsByPhone(smsCodeSendReq.getPhone())) {
            throw new BizException(ErrorCode.FREQUENT_ERROR, "短信发送过于频繁，请稍后再试");
        }
        smsCodeManager.sendCode(smsCodeSendReq);
        return Result.ok("验证码已发送");
    }

    /** 当前用户信息（含 RBAC 角色/权限填充）。 */
    @GetMapping("/info")
    public Result<UserInfoResp> getUserInfo() {
        Long userId = UserContext.getUserId();
        UserInfoResp userInfoResp = userManager.getByUserId(userId);

        // V5.0 RBAC: 加载角色+权限填充到响应（失败不阻断基本信息返回）
        try {
            UserInfo userInfo = userInfoManager.loadUserById(userId);
            if (userInfo.roles() != null) userInfoResp.setRoles(userInfo.roles());
            if (userInfo.permissions() != null) userInfoResp.setPermissions(userInfo.permissions());
        } catch (Exception e) {
            log.warn("Failed to load RBAC info for userId={}: {}", userId, e.getMessage());
        }

        return Result.ok(userInfoResp);
    }

    /**
     * 增量更新用户资料（大厂标准 PATCH）。
     * 支持 updateMask 显式声明要更新的字段（"nickname", "avatar"）。
     * 返回全量 UserInfoResp（先查后全量更新）。
     */
    @PatchMapping("/profile")
    public Result<UserInfoResp> patchProfile(@RequestBody UpdateProfileReq req) {
        Long userId = UserContext.getUserId();
        return Result.ok(userProfileManager.patchProfile(userId, req));
    }
}
