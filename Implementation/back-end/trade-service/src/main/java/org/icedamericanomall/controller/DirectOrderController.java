package org.icedamericanomall.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.dto.DirectOrderReq;
import org.icedamericanomall.domain.vo.OrderVO;
import org.icedamericanomall.manager.OrderCreationManager;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

/**
 * V4.0: 立即购买 — 跳过购物车直接下单（京东标准）。
 * <p>
 * 与购物车下单的区别: 不读取购物车、下单后不清除购物车。
 */
@RestController
@RequestMapping("/api/trade/order")
@RequiredArgsConstructor
public class DirectOrderController {

    private final OrderCreationManager orderCreationManager;

    @PostMapping("/direct")
    public Result<OrderVO> directOrder(@Valid @RequestBody DirectOrderReq req) {
        Long userId = UserContext.getUserId();
        return Result.ok(orderCreationManager.createOrderDirect(userId, req));
    }
}
