package org.icedamericanomall.trade.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.trade.adapter.request.CreateOrderReq;
import org.icedamericanomall.trade.adapter.request.DirectOrderReq;
import org.icedamericanomall.trade.adapter.request.OrderPageReq;
import org.icedamericanomall.trade.adapter.response.OrderVO;
import org.icedamericanomall.trade.app.order.OrderCreationManager;
import org.icedamericanomall.trade.app.order.OrderLifecycleManager;
import org.icedamericanomall.trade.app.order.OrderQueryManager;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateContext;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trade/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCreationManager orderCreationManager;
    private final OrderLifecycleManager orderLifecycleManager;
    private final OrderQueryManager orderQueryManager;

    // ==================== 下单 (USER/VIP/SVIP 共享) ====================

    /** 购物车下单 (orderType=NORMAL) */
    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody CreateOrderReq req) {
        Long userId = UserContext.getUserId();
        OrderCreateContext ctx = new OrderCreateContext();
        ctx.setUserId(userId);
        ctx.setOrderType(OrderTypeEnum.NORMAL);
        ctx.setAddressId(req.getAddressId());
        ctx.setUserCouponId(req.getUserCouponId());
        return Result.ok(orderCreationManager.createOrder(ctx));
    }

    /** V4.0: 立即购买 — 跳过购物车直接下单（京东标准, orderType=DIRECT） */
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

    /** 订单详情 —— 归属校验下沉至仓储（不存在→ORDER_NOT_FOUND，非本人→FORBIDDEN）。 */
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderQueryManager.getOwnedOrderDetail(orderNo, UserContext.getUserId()));
    }

    /** 我的订单分页（query: status / page / size，属性绑定）。 */
    @GetMapping("/page")
    public Result<IPage<OrderVO>> page(OrderPageReq req) {
        return Result.ok(orderQueryManager.pageOrders(
                UserContext.getUserId(), null, req.getStatus(), req.getPage(), req.getSize()));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo) {
        orderLifecycleManager.cancelOrder(orderNo, UserContext.getUserId());
        return Result.ok();
    }

    @PostMapping("/{orderNo}/confirm")
    public Result<Void> confirm(@PathVariable String orderNo) {
        orderLifecycleManager.confirmReceipt(orderNo, UserContext.getUserId());
        return Result.ok();
    }
}
