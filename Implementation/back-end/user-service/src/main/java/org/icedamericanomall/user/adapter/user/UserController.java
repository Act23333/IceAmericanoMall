package org.icedamericanomall.user.adapter.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.user.domain.dto.UpdateProfileReq;
import org.icedamericanomall.user.service.SmsService;
import org.icedamericanomall.user.service.UserProfileService;
import org.icedamericanomall.user.service.UserService;
import org.icedamericanomall.user.domain.dto.SmsCodeSendReq;
import org.icedamericanomall.user.domain.vo.UserInfoResp;
import org.icedamericanomall.user.service.impl.UserInfoService;
import org.icedamericanomall.common.annotation.RateLimit;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserInfo;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.RateLimitUtils;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * 用户控制器 — V5.0 RBAC 增强
 * getUserInfo 返回角色+权限供前端 UI 权限判断
 */

@RequestMapping("/api/user")
@RestController
@Slf4j
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final SmsService smsService;
    private final UserProfileService userProfileService;
    private final RateLimitUtils rateLimitUtils;
    private final UserInfoService userInfoService;


    @PostMapping("/code")
    @RateLimit(key = "ip", limit = 10, duration = 1, unit = TimeUnit.MINUTES)
    public Result<Void> sendCode(@RequestBody SmsCodeSendReq smsCodeSendReq) {
        if (!rateLimitUtils.checkSmsByPhone(smsCodeSendReq.getPhone())) {
            throw new BizException(ErrorCode.FREQUENT_ERROR, "短信发送过于频繁，请稍后再试");
        }
        smsService.sendCode(smsCodeSendReq);
        return Result.ok("验证码已发送");
    }

    @GetMapping("/info")
    public Result<UserInfoResp> getUserInfo() {
        Long userId = UserContext.getUserId();
        UserInfoResp userInfoResp = userService.getByUserId(userId);

        // V5.0 RBAC: 加载角色+权限填充到响应
        try {
            UserInfo userInfo = userInfoService.loadUserById(userId);
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
        return Result.ok(userProfileService.patchProfile(userId, req));
    }




}