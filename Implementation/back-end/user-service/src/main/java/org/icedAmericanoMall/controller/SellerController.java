package org.icedAmericanoMall.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.dto.SellerRegisterReq;
import org.icedAmericanoMall.domain.dto.UpdateShopReq;
import org.icedAmericanoMall.domain.entity.SellerEntity;
import org.icedAmericanoMall.service.SellerService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端 — 店铺设置 & 注册申请
 *
 * <pre>
 * Scenario: 用户申请成为商家
 *   Given 用户已登录且尚未成为商家
 *   When POST /api/seller/register with shop info
 *   Then 创建商家记录，状态为"审核中"
 *   And 返回成功
 *
 * Scenario: 商家获取店铺信息
 *   Given 用户已登录且是商家
 *   When GET /api/seller/shop
 *   Then 返回店铺名称/Logo/联系电话/地址
 *
 * Scenario: 商家更新店铺信息
 *   Given 用户已登录且是商家
 *   When PUT /api/seller/shop with update fields
 *   Then null-safe 部分更新
 *   And 返回成功
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
        Long userId = UserContext.getUser();
        SellerEntity seller = new SellerEntity();
        seller.setUserId(userId);
        seller.setShopName(req.getShopName());
        seller.setContactPhone(req.getContactPhone());
        seller.setProvince(req.getProvince());
        seller.setCity(req.getCity());
        seller.setDistrict(req.getDistrict());
        seller.setDetailAddress(req.getDetailAddress());
        sellerService.register(seller);
        return Result.ok();
    }

    /** 获取当前商家店铺信息 */
    @GetMapping("/shop")
    public Result<SellerEntity> getShop() {
        Long userId = UserContext.getUser();
        SellerEntity seller = sellerService.getByUserId(userId);
        if (seller == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "您尚未开通商家");
        }
        return Result.ok(seller);
    }

    /** 更新店铺信息 */
    @PutMapping("/shop")
    public Result<Void> updateShop(@Valid @RequestBody UpdateShopReq req) {
        Long userId = UserContext.getUser();
        SellerEntity seller = sellerService.getByUserId(userId);
        if (seller == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "您尚未开通商家");
        }
        // null-safe 部分更新
        var updater = sellerService.lambdaUpdate().eq(SellerEntity::getId, seller.getId());
        if (req.getShopName() != null && !req.getShopName().isBlank()) {
            updater.set(SellerEntity::getShopName, req.getShopName());
        }
        if (req.getShopLogo() != null && !req.getShopLogo().isBlank()) {
            updater.set(SellerEntity::getShopLogo, req.getShopLogo());
        }
        if (req.getContactPhone() != null && !req.getContactPhone().isBlank()) {
            updater.set(SellerEntity::getContactPhone, req.getContactPhone());
        }
        if (req.getProvince() != null) updater.set(SellerEntity::getProvince, req.getProvince());
        if (req.getCity() != null) updater.set(SellerEntity::getCity, req.getCity());
        if (req.getDistrict() != null) updater.set(SellerEntity::getDistrict, req.getDistrict());
        if (req.getDetailAddress() != null) updater.set(SellerEntity::getDetailAddress, req.getDetailAddress());
        updater.update();
        return Result.ok();
    }
}
