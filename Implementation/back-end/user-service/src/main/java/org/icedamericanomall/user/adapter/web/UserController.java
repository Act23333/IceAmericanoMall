package org.icedamericanomall.user.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.request.UpdateProfileReq;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.user.app.user.UserInfoManager;
import org.icedamericanomall.user.app.user.UserProfileManager;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器 — V5.0 RBAC 增强（/api/user 契约路径与报文保持不变）。
 *
 * <p>getUserInfo 返回角色+权限供前端 UI 权限判断；RBAC 合并编排（降级、roleType 派生）
 * 下沉至应用层 {@link UserInfoManager}，Controller 只做一行调用。
 *
 * <p>发码链路已整体迁出：端点 {@code /api/user/code} 删除，前端改调 verification-service
 * 公开端点 {@code POST /api/verification/codes}（channel=SMS，ADR-016 收尾）。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserInfoManager userInfoManager;
    private final UserProfileManager userProfileManager;

    /** 当前用户信息（含 RBAC 角色/权限填充；roleType 由五表角色派生，RBAC 数据经 ADR-018 调授权域获取）。 */
    @GetMapping("/info")
    public Result<UserInfoResp> getUserInfo() {
        return Result.ok(userInfoManager.getUserInfoWithRbac(UserContext.getUserId()));
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
