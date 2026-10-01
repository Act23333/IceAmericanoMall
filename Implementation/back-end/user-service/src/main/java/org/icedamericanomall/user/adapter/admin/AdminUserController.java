package org.icedamericanomall.user.adapter.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.user.adapter.response.SellerVO;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.user.app.rbac.RoleManageManager;
import org.icedamericanomall.user.app.seller.SellerManager;
import org.icedamericanomall.user.app.user.UserManager;
import org.icedamericanomall.common.domain.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员用户管理 — Interface 层
 *
 * DDD: Controller 仅参数解析 + 委托 Service + 封装 Result。
 * Mapper 操作全部下沉到 Service 层。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("@ss.hasPermi('user:admin')")
public class AdminUserController {

    private static final int SELLER_STATUS_APPROVED = 1;

    private final UserManager userManager;
    private final SellerManager sellerManager;
    private final RoleManageManager roleManageManager;

    public AdminUserController(UserManager userManager, SellerManager sellerManager,
                                RoleManageManager roleManageManager) {
        this.userManager = userManager;
        this.sellerManager = sellerManager;
        this.roleManageManager = roleManageManager;
    }

    @GetMapping("/user/page")
    public Result<IPage<UserInfoResp>> pageUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userManager.pageUsers(page, size));
    }

    @PutMapping("/user/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        userManager.updateStatus(id, status);
        return Result.ok();
    }

    @GetMapping("/seller/pending")
    public Result<IPage<SellerVO>> pendingSellers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(sellerManager.pagePending(page, size));
    }

    @PutMapping("/seller/{id}/approve")
    public Result<Void> approveSeller(@PathVariable Long id) {
        sellerManager.updateStatus(id, SELLER_STATUS_APPROVED);
        return Result.ok();
    }

    /** 修改用户角色 — 同时更新 user.role_type + sys_user_role */
    @PutMapping("/user/{id}/role")
    public Result<Void> setUserRole(@PathVariable Long id, @RequestParam Integer roleType) {
        userManager.updateRole(id, roleType);
        roleManageManager.setUserRole(id, roleType);
        return Result.ok();
    }
}
