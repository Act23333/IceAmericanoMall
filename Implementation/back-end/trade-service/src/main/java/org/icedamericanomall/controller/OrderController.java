package org.icedamericanomall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.convert.OrderConverter;
import org.icedamericanomall.domain.dto.CreateOrderReq;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.domain.vo.OrderVO;
import org.icedamericanomall.manager.OrderCreationManager;
import org.icedamericanomall.manager.OrderLifecycleManager;
import org.icedamericanomall.service.OrderService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trade/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCreationManager orderCreationManager;
    private final OrderLifecycleManager orderLifecycleManager;
    private final OrderService orderService;
    private final OrderConverter orderConverter;

    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody CreateOrderReq req) {
        return Result.ok(orderCreationManager.createOrder(UserContext.getUserId(), req));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        OrderEntity order = orderService.getByOrderNo(orderNo);
        if (order == null) {
            return Result.error(404, "订单不存在");
        }
        OrderVO vo = orderConverter.entityToVO(order);
        vo.setItems(orderConverter.itemEntitiesToVOs(orderService.listItems(order.getId())));
        return Result.ok(vo);
    }

    @GetMapping("/page")
    public Result<IPage<OrderVO>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<OrderEntity> entityPage = orderService.pageOrders(UserContext.getUserId(), null, status, page, size);
        return Result.ok(entityPage.convert(orderConverter::entityToVO));
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
