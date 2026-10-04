package org.icedamericanomall.trade.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.trade.adapter.request.OrderPageReq;
import org.icedamericanomall.trade.adapter.request.ShipOrderReq;
import org.icedamericanomall.trade.adapter.response.OrderVO;
import org.icedamericanomall.trade.app.order.OrderLifecycleManager;
import org.icedamericanomall.trade.app.order.OrderQueryManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("@ss.hasPermi('seller:admin')")
@RequestMapping("/api/trade/seller/order")
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderLifecycleManager orderLifecycleManager;
    private final OrderQueryManager orderQueryManager;

    /** 本店订单分页（query: status / page / size，属性绑定）。 */
    @GetMapping("/page")
    public Result<IPage<OrderVO>> page(OrderPageReq req) {
        return Result.ok(orderQueryManager.pageOrders(
                null, UserContext.getUserId(), req.getStatus(), req.getPage(), req.getSize()));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderQueryManager.getSellerOrderDetail(orderNo, UserContext.getUserId()));
    }

    /**
     * 发货（V5.6 拆单发货）：{@code skuIds} 为本包裹明细，空/缺省 = 全部剩余明细（单包裹语义）。
     */
    @PostMapping("/{orderNo}/ship")
    public Result<Void> ship(@PathVariable String orderNo, @Valid @RequestBody ShipOrderReq req) {
        orderLifecycleManager.shipOrder(orderNo, UserContext.getUserId(), req.getLogisticsNumber(),
                req.getLogisticsCompany(), req.getSkuIds());
        return Result.ok();
    }
}
