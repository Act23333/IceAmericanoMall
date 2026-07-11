package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.convert.OrderConverter;
import org.icedAmericanoMall.domain.dto.CreateOrderReq;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.vo.OrderVO;
import org.icedAmericanoMall.manager.OrderManager;
import org.icedAmericanoMall.service.OrderService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trade/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderManager orderManager;
    private final OrderService orderService;
    private final OrderConverter orderConverter;

    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody CreateOrderReq req) {
        return Result.ok(orderManager.createOrder(UserContext.getUser(), req));
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
        IPage<OrderEntity> entityPage = orderService.pageMyOrders(UserContext.getUser(), status, page, size);
        return Result.ok(entityPage.convert(orderConverter::entityToVO));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo) {
        orderManager.cancelOrder(orderNo, UserContext.getUser());
        return Result.ok();
    }

    @PostMapping("/{orderNo}/confirm")
    public Result<Void> confirm(@PathVariable String orderNo) {
        orderManager.confirmReceipt(orderNo, UserContext.getUser());
        return Result.ok();
    }
}
