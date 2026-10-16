package org.icedamericanomall.trade.adapter.web;

import org.icedamericanomall.common.web.QueryModel;
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

    /**
     * 本店订单分页（T10；{@code @QueryModel} 绑定，线上名 snake_case）。
     *
     * <p>筛选（{@code keyword}/{@code start_date}/{@code end_date}）与状态一律<b>后端</b>执行
     * （A2 收口），控制器签名保持不变。
     */
    @GetMapping("/page")
    public Result<IPage<OrderVO>> page(@QueryModel OrderPageReq req) {
        return Result.ok(orderQueryManager.pageOrders(
                null, UserContext.getUserId(), req.getStatus(), req.parseKeyword(),
                req.parseStartDateTime(), req.parseEndDateTime(), req.getPage(), req.getSize()));
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
