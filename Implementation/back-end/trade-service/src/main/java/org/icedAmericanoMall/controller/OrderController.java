package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.convert.OrderConverter;
import org.icedAmericanoMall.domain.dto.CreateOrderReq;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.OrderItemEntity;
import org.icedAmericanoMall.domain.vo.OrderVO;
import org.icedAmericanoMall.manager.OrderManager;
import org.icedAmericanoMall.mapper.OrderItemMapper;
import org.icedAmericanoMall.service.OrderService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trade/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderManager orderManager;
    private final OrderService orderService;
    private final OrderItemMapper orderItemMapper;
    private final OrderConverter orderConverter;

    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody CreateOrderReq req) {
        Long userId = UserContext.getUser();
        OrderVO vo = orderManager.createOrder(userId, req);
        return Result.ok(vo);
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        OrderEntity order = orderService.getByOrderNo(orderNo);
        if (order == null) {
            return Result.error(404, "订单不存在");
        }
        OrderVO vo = orderConverter.entityToVO(order);
        List<OrderItemEntity> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItemEntity>()
                        .eq(OrderItemEntity::getOrderId, order.getId()));
        vo.setItems(orderConverter.itemEntitiesToVOs(items));
        return Result.ok(vo);
    }

    @GetMapping("/page")
    public Result<IPage<OrderVO>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUser();
        IPage<OrderEntity> entityPage = orderService.pageMyOrders(userId, status, page, size);
        IPage<OrderVO> voPage = entityPage.convert(orderConverter::entityToVO);
        return Result.ok(voPage);
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo) {
        Long userId = UserContext.getUser();
        orderService.cancelOrder(orderNo, userId);
        return Result.ok();
    }

    @PostMapping("/{orderNo}/confirm")
    public Result<Void> confirm(@PathVariable String orderNo) {
        Long userId = UserContext.getUser();
        orderService.confirmReceipt(orderNo, userId);
        return Result.ok();
    }
}
