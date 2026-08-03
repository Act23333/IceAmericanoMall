package org.icedamericanomall.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.convert.OrderConverter;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.domain.vo.OrderVO;
import org.icedamericanomall.manager.AdminManager;
import org.icedamericanomall.service.OrderService;
import org.noLazy.common.domain.Result;
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
    private final OrderService orderService;
    private final OrderConverter orderConverter;

    /** 仪表盘：用户数、订单数、营收统计。 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(adminManager.dashboard());
    }

    /** 全部订单分页（不限用户/商家）。 */
    @GetMapping("/orders/page")
    public Result<IPage<OrderVO>> pageAllOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status) {
        IPage<OrderEntity> entityPage = orderService.pageOrders(null, null, status, page, size);
        return Result.ok(entityPage.convert(orderConverter::entityToVO));
    }

    /** 近 N 天订单趋势（图表数据）。 */
    @GetMapping("/stats/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(adminManager.orderTrend(days));
    }

    /** 销量 Top N 商品（V2.5 详细统计）。 */
    @GetMapping("/stats/top-products")
    public Result<List<Map<String, Object>>> topProducts(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(adminManager.topProducts(limit));
    }

    /** 指定日期区间分日销售额（V2.5 详细统计）。 */
    @GetMapping("/stats/sales")
    public Result<List<Map<String, Object>>> salesByDateRange(
            @RequestParam String from, @RequestParam String to) {
        return Result.ok(adminManager.saleByDateRange(java.time.LocalDate.parse(from), java.time.LocalDate.parse(to)));
    }
}
