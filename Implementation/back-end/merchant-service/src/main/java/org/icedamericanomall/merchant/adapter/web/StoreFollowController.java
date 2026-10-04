package org.icedamericanomall.merchant.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.UnauthorizedException;
import org.icedamericanomall.merchant.app.seller.StoreFollowManager;
import org.springframework.web.bind.annotation.*;

/**
 * 关注 / 取消关注店铺（京东标准 — 店铺关注；V5.4 起以 shopId 记账，ADR-019）。
 *
 * <p>参数二选一：新接入方传 {@code shopId}（请求参数），遗留前端仍可走
 * {@code /follow/{sellerId}} 路径（解析为该商家默认店铺）。
 * Controller 仅做参数校验 + Result 包装，Redis 与解析逻辑均在
 * {@link StoreFollowManager} / {@code ShopManager}。
 *
 * <pre>
 * POST   /api/shop/follow?shopId=1          新：按店铺关注
 * POST   /api/shop/follow/{sellerId}        遗留：按商家 user_id 关注默认店铺
 * DELETE 同上（取消关注）
 * GET    /api/shop/follow/status|{sellerId}/status
 * GET    /api/shop/follow/count|{sellerId}/count
 * </pre>
 */
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class StoreFollowController {

    private final StoreFollowManager storeFollowManager;

    // ==================== 新规范：按 shopId ====================

    /** 关注店铺（shopId 口径）。 */
    @PostMapping("/follow")
    public Result<Boolean> followByShopId(@RequestParam Long shopId) {
        storeFollowManager.follow(requireUserId(), shopId, null);
        return Result.ok(true);
    }

    /** 取消关注（shopId 口径）。 */
    @DeleteMapping("/follow")
    public Result<Boolean> unfollowByShopId(@RequestParam Long shopId) {
        storeFollowManager.unfollow(requireUserId(), shopId, null);
        return Result.ok(false);
    }

    /** 查询当前用户是否已关注（shopId 口径）。 */
    @GetMapping("/follow/status")
    public Result<Boolean> isFollowingByShopId(@RequestParam Long shopId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.ok(false);
        }
        return Result.ok(storeFollowManager.isFollowing(userId, shopId, null));
    }

    /** 店铺粉丝数（shopId 口径）。 */
    @GetMapping("/follow/count")
    public Result<Long> followerCountByShopId(@RequestParam Long shopId) {
        return Result.ok(storeFollowManager.followerCount(shopId, null));
    }

    // ==================== 遗留兼容：按 sellerId（商家 user_id → 默认店铺） ====================

    /**
     * 关注店铺（遗留 sellerId 口径）。
     *
     * @deprecated 改用 {@code POST /api/shop/follow?shopId=}。
     */
    @Deprecated
    @PostMapping("/follow/{sellerId}")
    public Result<Boolean> follow(@PathVariable Long sellerId) {
        storeFollowManager.follow(requireUserId(), null, sellerId);
        return Result.ok(true);
    }

    /**
     * 取消关注（遗留 sellerId 口径）。
     *
     * @deprecated 改用 {@code DELETE /api/shop/follow?shopId=}。
     */
    @Deprecated
    @DeleteMapping("/follow/{sellerId}")
    public Result<Boolean> unfollow(@PathVariable Long sellerId) {
        storeFollowManager.unfollow(requireUserId(), null, sellerId);
        return Result.ok(false);
    }

    /**
     * 关注状态（遗留 sellerId 口径）。
     *
     * @deprecated 改用 {@code GET /api/shop/follow/status?shopId=}。
     */
    @Deprecated
    @GetMapping("/follow/{sellerId}/status")
    public Result<Boolean> isFollowing(@PathVariable Long sellerId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.ok(false);
        }
        return Result.ok(storeFollowManager.isFollowing(userId, null, sellerId));
    }

    /**
     * 店铺粉丝数（遗留 sellerId 口径）。
     *
     * @deprecated 改用 {@code GET /api/shop/follow/count?shopId=}。
     */
    @Deprecated
    @GetMapping("/follow/{sellerId}/count")
    public Result<Long> followerCount(@PathVariable Long sellerId) {
        return Result.ok(storeFollowManager.followerCount(null, sellerId));
    }

    /** 登录守卫（网关已鉴权，此处兜底；未登录抛 UNAUTHORIZED）。 */
    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }
}
