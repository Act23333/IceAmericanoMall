package org.icedamericanomall.user.adapter.web.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.user.app.user.UserAdminManager;
import org.icedamericanomall.common.api.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员用户管理 — Interface 层（档案域管理职责）。
 *
 * <p>角色授予（{@code /user/{id}/role}）经应用层 {@link UserAdminManager} 委托
 * authorization-service（ADR-018：RBAC 归属授权域）。
 * 商家入驻审核已随商家域迁出至 merchant-service（{@code /api/admin/seller/**}）。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("@ss.hasPermi('user:admin')")
public class AdminUserController {

    private final UserAdminManager userAdminManager;

    public AdminUserController(UserAdminManager userAdminManager) {
        this.userAdminManager = userAdminManager;
    }

    /** 分页查询用户列表（roleType 由 RBAC 五表角色派生）。 */
    @GetMapping("/user/page")
    public Result<IPage<UserInfoResp>> pageUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userAdminManager.pageUsers(page, size));
    }

    /** 启用/禁用用户（status 见 UserStatusEnum）。 */
    @PutMapping("/user/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        userAdminManager.updateStatus(id, status);
        return Result.ok();
    }

    /**
     * 修改用户角色 —— V5.2 只写 RBAC 五表（sys_user_role 为唯一权威口径）；
     * {@code user.role_type} 列退役（代码不读写，响应 roleType 由五表派生）。
     * 经 ADR-018 委托 authorization-service 执行，变更后远端主动失效该用户的权限缓存。
     */
    @PutMapping("/user/{id}/role")
    public Result<Void> setUserRole(@PathVariable Long id, @RequestParam(name = "role_type") Integer roleType) {
        userAdminManager.setUserRole(id, roleType);
        return Result.ok();
    }
}
