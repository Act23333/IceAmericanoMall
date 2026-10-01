package org.icedamericanomall.trade.adapter.seller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.convert.OrderConverter;
import org.icedamericanomall.trade.domain.dto.ShipOrderReq;
import org.icedamericanomall.trade.domain.entity.OrderEntity;
import org.icedamericanomall.trade.domain.vo.OrderVO;
import org.icedamericanomall.trade.manager.OrderLifecycleManager;
import org.icedamericanomall.trade.service.OrderService;
import org.icedamericanomall.common.domain.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("@ss.hasPermi('seller:admin')")
@RequestMapping("/api/trade/seller/order")
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderLifecycleManager orderLifecycleManager;
    private final OrderService orderService;
    private final OrderConverter orderConverter;

    @GetMapping("/page")
    public Result<IPage<OrderVO>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long sellerId = UserContext.getUserId();
        IPage<OrderEntity> entityPage = orderService.pageOrders(null, sellerId, status, page, size);
        return Result.ok(entityPage.convert(orderConverter::entityToVO));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        Long sellerId = UserContext.getUserId();
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
        Long sellerId = UserContext.getUserId();
        orderLifecycleManager.shipOrder(orderNo, sellerId, req.getLogisticsNumber(), req.getLogisticsCompany());
        return Result.ok();
    }
}
