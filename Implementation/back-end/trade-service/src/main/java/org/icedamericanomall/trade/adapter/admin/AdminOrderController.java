package org.icedamericanomall.trade.adapter.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.trade.adapter.request.OrderPageReq;
import org.icedamericanomall.trade.adapter.response.OrderVO;
import org.icedamericanomall.trade.app.dashboard.AdminManager;
import org.icedamericanomall.trade.app.order.OrderQueryManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员后台 — 仪表盘 & 订单管理。统计聚合与跨服务调用下沉至 {@link AdminManager}。
 */
@RestController
@PreAuthorize("@ss.hasPermi('user:admin')")
@RequestMapping("/api/trade/admin")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminManager adminManager;
    private final OrderQueryManager orderQueryManager;

    /** 仪表盘：用户数、订单数、营收统计。 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(adminManager.dashboard());
    }

    /** 全部订单分页（不限用户/商家；query: status / page / size，属性绑定）。 */
    @GetMapping("/orders/page")
    public Result<IPage<OrderVO>> pageAllOrders(OrderPageReq req) {
        return Result.ok(orderQueryManager.pageOrders(
                null, null, req.getStatus(), req.getPage(), req.getSize()));
    }

    /** 近 N 天订单趋势（图表数据）。 */
    @GetMapping("/stats/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(adminManager.orderTrend(days));
    }

    /** 销量 Top N 商品（V2.5 详细统计）。 */
    @GetMapping("/stats/top-products")
    public Result<List<Map<String, Object>>> topSpus(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(adminManager.topSpus(limit));
    }

    /** 指定日期区间分日销售额（V2.5 详细统计）。 */
    @GetMapping("/stats/sales")
    public Result<List<Map<String, Object>>> salesByDateRange(
            @RequestParam String from, @RequestParam String to) {
        return Result.ok(adminManager.saleByDateRange(java.time.LocalDate.parse(from), java.time.LocalDate.parse(to)));
    }
}
