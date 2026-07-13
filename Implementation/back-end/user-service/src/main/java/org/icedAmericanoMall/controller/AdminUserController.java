package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.vo.SellerVO;
import org.icedAmericanoMall.domain.vo.UserInfoResp;
import org.icedAmericanoMall.service.SellerService;
import org.icedAmericanoMall.service.UserService;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int SELLER_STATUS_APPROVED = 1;

    private final UserService userService;
    private final SellerService sellerService;

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

    @PutMapping("/user/{id}/role")
    public Result<Void> setUserRole(@PathVariable Long id, @RequestParam Integer roleType) {
        userService.updateRole(id, roleType);
        return Result.ok();
    }
}
