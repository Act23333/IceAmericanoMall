package org.icedamericanomall.user.adapter.open;

import org.icedamericanomall.user.app.seller.StoreFollowManager;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.web.bind.annotation.*;

/**
 * V3.5: 关注/取消关注商家（京东标准 — 店铺关注）。
 *
 * DDD COLA 合规 (V5.0): Controller 仅做参数校验 + Result 包装，
 * Redis 操作全部下沉到 {@link StoreFollowManager}。
 */
@RestController
@RequestMapping("/api/shop")
public class StoreFollowController {

    private final StoreFollowManager storeFollowManager;

    public StoreFollowController(StoreFollowManager storeFollowManager) {
        this.storeFollowManager = storeFollowManager;
    }

    @PostMapping("/follow/{sellerId}")
    public Result<Boolean> follow(@PathVariable Long sellerId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return Result.error(401, "请先登录");
        storeFollowManager.follow(userId, sellerId);
        return Result.ok(true);
    }

    @DeleteMapping("/follow/{sellerId}")
    public Result<Boolean> unfollow(@PathVariable Long sellerId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return Result.error(401, "请先登录");
        storeFollowManager.unfollow(userId, sellerId);
        return Result.ok(false);
    }

    @GetMapping("/follow/{sellerId}/status")
    public Result<Boolean> isFollowing(@PathVariable Long sellerId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return Result.ok(false);
        return Result.ok(storeFollowManager.isFollowing(userId, sellerId));
    }

    @GetMapping("/follow/{sellerId}/count")
    public Result<Long> followerCount(@PathVariable Long sellerId) {
        return Result.ok(storeFollowManager.followerCount(sellerId));
    }
}
