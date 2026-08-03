package org.icedamericanomall.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.domain.vo.SellerVO;
import org.icedamericanomall.domain.vo.UserInfoResp;
import org.icedamericanomall.mapper.RoleMapper;
import org.icedamericanomall.service.SellerService;
import org.icedamericanomall.service.UserService;
import org.noLazy.common.domain.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * V5.0 RBAC: setUserRole 同时写入 RBAC 五表模型 sys_user_role
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("@ss.hasPermi('user:admin')")
public class AdminUserController {

    private static final int SELLER_STATUS_APPROVED = 1;
    /** roleType → sys_role.id 映射: 0→1(ROLE_USER), 1→3(ROLE_SELLER), 2→4(ROLE_ADMIN) */
    private static final Long[] ROLE_TYPE_TO_DB_ID = {1L, 3L, 4L};

    private final UserService userService;
    private final SellerService sellerService;
    private final RoleMapper roleMapper;

    public AdminUserController(UserService userService, SellerService sellerService, RoleMapper roleMapper) {
        this.userService = userService;
        this.sellerService = sellerService;
        this.roleMapper = roleMapper;
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

    /** V5.0 RBAC: 修改用户角色 — 同时更新 user.role_type 和 sys_user_role */
    @PutMapping("/user/{id}/role")
    public Result<Void> setUserRole(@PathVariable Long id, @RequestParam Integer roleType) {
        userService.updateRole(id, roleType);

        // 同步写入 RBAC 五表模型的 sys_user_role
        roleMapper.deleteUserRoles(id);
        if (roleType >= 0 && roleType < ROLE_TYPE_TO_DB_ID.length) {
            Long dbRoleId = ROLE_TYPE_TO_DB_ID[roleType];
            roleMapper.insertUserRole(id, dbRoleId);
            // 商家角色同时保留普通用户角色 (商家也是用户)
            if (roleType == 1) {
                roleMapper.insertUserRole(id, 1L); // ROLE_USER
            }
        }

        return Result.ok();
    }
}
