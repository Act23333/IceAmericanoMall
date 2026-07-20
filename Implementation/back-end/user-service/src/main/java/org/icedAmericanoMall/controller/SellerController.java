package org.icedAmericanoMall.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.dto.SellerRegisterReq;
import org.icedAmericanoMall.domain.dto.UpdateShopReq;
import org.icedAmericanoMall.domain.vo.SellerVO;
import org.icedAmericanoMall.service.SellerService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端 — 店铺设置 & 注册申请
 *
 * <pre>
 * Scenario: 用户申请成为商家
 *   Given 用户已登录且尚未成为商家
 *   When POST /api/seller/register with shop info
 *   Then 创建商家记录，状态为"审核中"，返回成功
 *
 * Scenario: 商家获取店铺信息
 *   Given 用户已登录且是商家
 *   When GET /api/seller/shop
 *   Then 返回店铺名称/Logo/联系电话/地址（VO）
 *
 * Scenario: 商家更新店铺信息
 *   Given 用户已登录且是商家
 *   When PUT /api/seller/shop with update fields
 *   Then null-safe 部分更新，返回成功
 * </pre>
 */
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    /** 用户申请成为商家 */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody SellerRegisterReq req) {
        sellerService.register(UserContext.getUserId(), req);
        return Result.ok();
    }

    /** 获取当前商家店铺信息 */
    @GetMapping("/shop")
    public Result<SellerVO> getShop() {
        return Result.ok(sellerService.getShopVO(UserContext.getUserId()));
    }

    /** 更新店铺信息 */
    @PutMapping("/shop")
    public Result<Void> updateShop(@Valid @RequestBody UpdateShopReq req) {
        sellerService.updateShop(UserContext.getUserId(), req);
        return Result.ok();
    }
}
