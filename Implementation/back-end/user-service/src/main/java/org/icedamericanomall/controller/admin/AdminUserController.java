package org.icedamericanomall.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.domain.vo.SellerVO;
import org.icedamericanomall.domain.vo.UserInfoResp;
import org.icedamericanomall.service.RoleManageService;
import org.icedamericanomall.service.SellerService;
import org.icedamericanomall.service.UserService;
import org.noLazy.common.domain.Result;
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

    private final UserService userService;
    private final SellerService sellerService;
    private final RoleManageService roleManageService;

    public AdminUserController(UserService userService, SellerService sellerService,
                                RoleManageService roleManageService) {
        this.userService = userService;
        this.sellerService = sellerService;
        this.roleManageService = roleManageService;
    }

    @GetMapping("/user/page")
    public Result<IPage<UserInfoResp>> pageUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.pageUsers(page, size));
    }

    @PutMapping("/user/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return Result.ok();
    }

    @GetMapping("/seller/pending")
    public Result<IPage<SellerVO>> pendingSellers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(sellerService.pagePending(page, size));
    }

    @PutMapping("/seller/{id}/approve")
    public Result<Void> approveSeller(@PathVariable Long id) {
        sellerService.updateStatus(id, SELLER_STATUS_APPROVED);
        return Result.ok();
    }

    /** 修改用户角色 — 同时更新 user.role_type + sys_user_role */
    @PutMapping("/user/{id}/role")
    public Result<Void> setUserRole(@PathVariable Long id, @RequestParam Integer roleType) {
        userService.updateRole(id, roleType);
        roleManageService.setUserRole(id, roleType);
        return Result.ok();
    }
}
