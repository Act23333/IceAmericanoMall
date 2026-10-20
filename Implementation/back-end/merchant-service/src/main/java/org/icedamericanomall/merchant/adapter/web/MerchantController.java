package org.icedamericanomall.merchant.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.merchant.adapter.request.MerchantApplyReq;
import org.icedamericanomall.merchant.adapter.request.ShopDecorationReq;
import org.icedamericanomall.merchant.adapter.request.UpdateShopReq;
import org.icedamericanomall.merchant.adapter.response.ShopDecorationVO;
import org.icedamericanomall.merchant.adapter.response.ShopVO;
import org.icedamericanomall.merchant.app.seller.MerchantManager;
import org.icedamericanomall.merchant.app.seller.ShopManager;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家端 — 入驻申请 & 店铺资料管理（ADR-018：非认证注册，账户创建在 authorization-service）。
 *
 * <pre>
 * Scenario: 用户申请成为商家
 *   Given 用户已登录且尚未成为商家
 *   When POST /api/seller/apply with shop info
 *   Then 创建商家身份（审核中）+ 默认店铺（审核中），返回成功
 *
 * Scenario: 商家查询自己的店铺列表
 *   Given 用户已登录且是商家
 *   When GET /api/seller/shops
 *   Then 返回该商家全部店铺（ShopVO 列表）
 *
 * Scenario: 商家更新自有店铺资料
 *   Given 用户已登录且是商家，且目标店铺属于该商家
 *   When PUT /api/seller/shops/{shopId} with update fields
 *   Then null-safe 部分更新，返回成功；非本人店铺 → FORBIDDEN
 * </pre>
 *
 * <p>遗留别名：{@code GET/PUT /api/seller/shop}（默认店铺）保留一个版本周期，
 * 见方法级 {@code @Deprecated}。
 */
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantManager merchantManager;
    private final ShopManager shopManager;

    /** 用户申请成为商家（入驻申请；原 /api/seller/register，命名消除"认证注册"误导）。 */
    @PostMapping("/apply")
    public Result<Void> apply(@Valid @RequestBody MerchantApplyReq req) {
        merchantManager.register(UserContext.getUserId(), req);
        return Result.ok();
    }

    /**
     * 商家本人店铺列表。
     *
     * <p>多店铺入口（ADR-019）；单店铺商家返回长度为 1 的列表。
     */
    @PreAuthorize("@ss.hasPermi('seller:admin')")
    @GetMapping("/shops")
    public Result<List<ShopVO>> myShops() {
        return Result.ok(shopManager.listMyShops(UserContext.getUserId()));
    }

    /** 更新指定店铺资料（校验归属）。 */
    @PreAuthorize("@ss.hasPermi('seller:admin')")
    @PutMapping("/shops/{shopId}")
    public Result<Void> updateMyShop(@PathVariable Long shopId, @Valid @RequestBody UpdateShopReq req) {
        shopManager.updateMyShop(UserContext.getUserId(), shopId, req);
        return Result.ok();
    }

    /**
     * 查询店铺装修（S1c，ADR-048）。
     *
     * <pre>
     * Scenario: 商家查看自有店铺装修
     *   Given 商家已登录且目标店铺归属该商家
     *   When GET /api/seller/shops/{shopId}/decoration
     *   Then 返回 ShopDecorationVO；未装修时各字段为空值 / 空数组；非本人店铺 → FORBIDDEN
     * </pre>
     */
    @PreAuthorize("@ss.hasPermi('seller:admin')")
    @GetMapping("/shops/{shopId}/decoration")
    public Result<ShopDecorationVO> getDecoration(@PathVariable Long shopId) {
        return Result.ok(shopManager.getDecoration(UserContext.getUserId(), shopId));
    }

    /**
     * 保存店铺装修（S1d，ADR-048）—— PUT 全量替换，UPSERT。
     *
     * <pre>
     * Scenario: 商家保存店铺装修
     *   Given 商家已登录且目标店铺归属该商家
     *   When PUT /api/seller/shops/{shopId}/decoration with ShopDecorationReq
     *   Then 校验（公告 ≤200 / 轮播 ≤5 http(s) / 推荐位 ≤8 去重）并 UPSERT，返回成功；
     *        非本人店铺 → FORBIDDEN，内容不合法 → 400 PARAM_ERROR
     * </pre>
     */
    @PreAuthorize("@ss.hasPermi('seller:admin')")
    @PutMapping("/shops/{shopId}/decoration")
    public Result<Void> saveDecoration(@PathVariable Long shopId,
                                       @Valid @RequestBody ShopDecorationReq req) {
        shopManager.saveDecoration(UserContext.getUserId(), shopId, req);
        return Result.ok();
    }

    /**
     * 获取当前商家默认店铺信息。
     *
     * @deprecated 单店铺时代的别名，改用 {@code GET /api/seller/shops}。
     */
    @Deprecated
    @PreAuthorize("@ss.hasPermi('seller:admin')")
    @GetMapping("/shop")
    public Result<ShopVO> getDefaultShop() {
        return Result.ok(shopManager.getMyDefaultShop(UserContext.getUserId()));
    }

    /**
     * 更新默认店铺资料。
     *
     * @deprecated 改用 {@code PUT /api/seller/shops/{shopId}}。
     */
    @Deprecated
    @PreAuthorize("@ss.hasPermi('seller:admin')")
    @PutMapping("/shop")
    public Result<Void> updateDefaultShop(@Valid @RequestBody UpdateShopReq req) {
        shopManager.updateMyDefaultShop(UserContext.getUserId(), req);
        return Result.ok();
    }
}
