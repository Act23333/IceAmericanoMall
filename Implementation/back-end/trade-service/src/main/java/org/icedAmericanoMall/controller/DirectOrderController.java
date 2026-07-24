package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.dto.CreateOrderReq;
import org.icedAmericanoMall.manager.OrderCreationManager;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * V3.5: 立即购买 — 跳过购物车直接下单（京东标准）。
 */
@RestController
@RequestMapping("/api/trade/order")
@RequiredArgsConstructor
public class DirectOrderController {

    private final OrderCreationManager orderCreationManager;

    @PostMapping("/direct")
    public Result<?> directOrder(@RequestBody Map<String, Object> req) {
        Long userId = UserContext.getUserId();
        Long skuId = Long.valueOf(req.get("skuId").toString());
        int quantity = req.containsKey("quantity")
                ? Integer.parseInt(req.get("quantity").toString()) : 1;
        Long addressId = Long.valueOf(req.get("addressId").toString());

        // 构造购物车式下单请求（模拟购物车单商品）
        CreateOrderReq orderReq = new CreateOrderReq();
        orderReq.setAddressId(addressId);
        // 通过 OrderCreationManager 直接下单
        var result = orderCreationManager.createOrder(userId, orderReq);
        return Result.ok(result);
    }
}
