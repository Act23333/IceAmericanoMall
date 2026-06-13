package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.convert.OrderConverter;
import org.icedAmericanoMall.domain.dto.ShipOrderReq;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.vo.OrderVO;
import org.icedAmericanoMall.service.OrderService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trade/seller/order")
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderService orderService;
    private final OrderConverter orderConverter;

    @GetMapping("/page")
    public Result<IPage<OrderVO>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long sellerId = UserContext.getUser();
        IPage<OrderEntity> entityPage = orderService.pageSellerOrders(sellerId, status, page, size);
        IPage<OrderVO> voPage = entityPage.convert(orderConverter::entityToVO);
        return Result.ok(voPage);
    }

    @PostMapping("/{orderNo}/ship")
    public Result<Void> ship(@PathVariable String orderNo, @RequestBody ShipOrderReq req) {
        Long sellerId = UserContext.getUser();
        orderService.shipOrder(orderNo, sellerId,
                req.getLogisticsNumber(), req.getLogisticsCompany());
        return Result.ok();
    }
}
