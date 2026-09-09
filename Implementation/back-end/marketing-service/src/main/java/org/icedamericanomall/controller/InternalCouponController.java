package org.icedamericanomall.controller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.CouponEntity;
import org.icedamericanomall.dto.CouponStackInfoDTO;
import org.icedamericanomall.service.CouponService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 优惠券内部接口 —— 供 trade-service 下单时抵扣/回滚/查询叠加规则（Feign 调用）。
 * {@code /internal/**} 路径：异常直接透传，不包 Result。
 */
@RestController
@RequestMapping("/internal/coupon")
@RequiredArgsConstructor
public class InternalCouponController {

    private final CouponService couponService;

    /** V4.3: 使用优惠券，返回实际抵扣金额（分）。新增 productIds+categoryIds 用于 scope 校验 */
    @PostMapping("/use")
    public int use(@RequestParam Long userId, @RequestParam Long userCouponId,
                   @RequestParam String orderNo, @RequestParam Integer orderAmount,
                   @RequestParam Integer orderType, @RequestParam Long sellerId,
                   @RequestParam(required = false) String productIds,
                   @RequestParam(required = false) String categoryIds) {
        return couponService.useCoupon(userId, userCouponId, orderNo, orderAmount,
                orderType, sellerId, productIds, categoryIds);
    }

    /** 按订单号回滚优惠券。 */
    @PostMapping("/rollback")
    public void rollback(@RequestParam String orderNo) {
        couponService.rollbackByOrderNo(orderNo);
    }

    /** V4.3: 批量查询券模板（下单时校验多券叠加规则）。返回轻量 DTO 避免 full entity 序列化。 */
    @GetMapping("/batch")
    public List<CouponStackInfoDTO> batchGet(@RequestParam List<Long> ids) {
        List<CouponEntity> entities = couponService.getBatchByIds(ids);
        List<CouponStackInfoDTO> dtos = new ArrayList<>();
        for (CouponEntity e : entities) {
            CouponStackInfoDTO d = new CouponStackInfoDTO();
            d.setId(e.getId());
            d.setCouponId(e.getCouponId());
            d.setCouponCategory(e.getCouponCategory());
            d.setStackRule(e.getStackRule());
            d.setStackGroup(e.getStackGroup());
            d.setDiscountType(e.getDiscountType());
            d.setSellerId(e.getSellerId());
            dtos.add(d);
        }
        return dtos;
    }
}
