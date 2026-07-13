package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.convert.OrderConverter;
import org.icedAmericanoMall.domain.dto.ShipOrderReq;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.vo.OrderVO;
import org.icedAmericanoMall.manager.OrderManager;
import org.icedAmericanoMall.service.OrderService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.ForbiddenException;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trade/seller/order")
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderManager orderManager;
    private final OrderService orderService;
    private final OrderConverter orderConverter;

    @GetMapping("/page")
    public Result<IPage<OrderVO>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long sellerId = UserContext.getUser();
        IPage<OrderEntity> entityPage = orderService.pageSellerOrders(sellerId, status, page, size);
        return Result.ok(entityPage.convert(orderConverter::entityToVO));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        Long sellerId = UserContext.getUser();
        OrderEntity order = orderService.getByOrderNo(orderNo);
        if (order == null) return Result.error(404, "订单不存在");
        if (!order.getSellerId().equals(sellerId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "无权查看该订单");
        }
        OrderVO vo = orderConverter.entityToVO(order);
        vo.setItems(orderConverter.itemEntitiesToVOs(orderService.listItems(order.getId())));
        return Result.ok(vo);
    }

    @PostMapping("/{orderNo}/ship")
    public Result<Void> ship(@PathVariable String orderNo, @RequestBody ShipOrderReq req) {
        Long sellerId = UserContext.getUser();
        orderManager.shipOrder(orderNo, sellerId, req.getLogisticsNumber(), req.getLogisticsCompany());
        return Result.ok();
    }
}
