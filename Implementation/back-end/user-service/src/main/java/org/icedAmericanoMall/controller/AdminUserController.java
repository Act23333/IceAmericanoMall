package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.service.SellerService;
import org.icedAmericanoMall.service.UserService;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final SellerService sellerService;

    @GetMapping("/user/page")
    public Result<IPage<?>> pageUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var entityPage = userService.page(new Page<>(page, size));
        return Result.ok(entityPage);
    }

    @PutMapping("/user/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.lambdaUpdate().set(
                org.icedAmericanoMall.domain.entity.UserEntity::getStatus,
                org.icedAmericanoMall.constants.UserStatusEnum.NORMAL.getCode() == status
                        ? org.icedAmericanoMall.constants.UserStatusEnum.NORMAL
                        : org.icedAmericanoMall.constants.UserStatusEnum.FROZEN)
                .eq(org.icedAmericanoMall.domain.entity.UserEntity::getId, id)
                .update();
        return Result.ok();
    }

    @GetMapping("/seller/pending")
    public Result<?> pendingSellers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var entityPage = sellerService.lambdaQuery()
                .eq(org.icedAmericanoMall.domain.entity.SellerEntity::getStatus, 0)
                .page(new Page<>(page, size));
        return Result.ok(entityPage);
    }

    @PutMapping("/seller/{id}/approve")
    public Result<Void> approveSeller(@PathVariable Long id) {
        sellerService.updateStatus(id, 1); // 正常
        return Result.ok();
    }
}
