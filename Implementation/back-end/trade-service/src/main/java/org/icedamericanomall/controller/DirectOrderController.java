package org.icedamericanomall.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.dto.DirectOrderReq;
import org.icedamericanomall.domain.vo.OrderVO;
import org.icedamericanomall.enums.OrderTypeEnum;
import org.icedamericanomall.manager.OrderCreationManager;
import org.icedamericanomall.strategy.OrderCreateContext;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

/**
 * V4.0: 立即购买 — 跳过购物车直接下单（京东标准）。
 * V4.1: 适配统一订单中心（策略模式路由，orderType=DIRECT）。
 */
@RestController
@RequestMapping("/api/trade/order")
@RequiredArgsConstructor
public class DirectOrderController {

    private final OrderCreationManager orderCreationManager;

    @PostMapping("/direct")
    public Result<OrderVO> directOrder(@Valid @RequestBody DirectOrderReq req) {
        Long userId = UserContext.getUserId();
        OrderCreateContext ctx = new OrderCreateContext();
        ctx.setUserId(userId);
        ctx.setOrderType(OrderTypeEnum.DIRECT);
        ctx.setSkuId(req.getSkuId());
        ctx.setQuantity(req.getQuantity());
        ctx.setAddressId(req.getAddressId());
        ctx.setUserCouponId(req.getUserCouponId());
        return Result.ok(orderCreationManager.createOrder(ctx));
    }
}
