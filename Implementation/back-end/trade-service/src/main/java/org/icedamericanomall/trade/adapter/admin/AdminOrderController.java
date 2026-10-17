package org.icedamericanomall.trade.adapter.admin;

import org.icedamericanomall.common.web.QueryModel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.trade.adapter.request.OrderPageReq;
import org.icedamericanomall.trade.adapter.response.AdminDashboardVO;
import org.icedamericanomall.trade.adapter.response.OrderTrendVO;
import org.icedamericanomall.trade.adapter.response.OrderVO;
import org.icedamericanomall.trade.adapter.response.SalesByDateVO;
import org.icedamericanomall.trade.adapter.response.SpuStatVO;
import org.icedamericanomall.trade.app.dashboard.AdminManager;
import org.icedamericanomall.trade.app.order.OrderQueryManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员后台 — 仪表盘 & 订单管理。统计聚合与跨服务调用下沉至 {@link AdminManager}。
 *
 * <p><b>批次 D（{@code Result<Map>} → 类型化 VO）</b>：AD11/AD13/AD14/AD15 四个统计端点的
 * 出参由 {@code Result<Map<String,Object>>} / {@code Result<List<Map<String,Object>>>} 改为
 * {@link AdminDashboardVO} / {@code List<OrderTrendVO>} / {@code List<SpuStatVO>} /
 * {@code List<SalesByDateVO>} —— 装配全部在 {@link AdminManager}，Controller 只透出。
 * 金额字段一律<b>整数分</b>（口径见各 VO 字段 javadoc）。
 */
@RestController
@PreAuthorize("@ss.hasPermi('user:admin')")
@RequestMapping("/api/trade/admin")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminManager adminManager;
    private final OrderQueryManager orderQueryManager;

    /** 仪表盘：用户数、订单数、营收统计（营收为分）。 */
    @GetMapping("/dashboard")
    public Result<AdminDashboardVO> dashboard() {
        return Result.ok(adminManager.dashboard());
    }

    /**
     * 全部订单分页（AD12；{@code @QueryModel} 绑定，线上名 snake_case）。
     *
     * <p>{@code keyword}/{@code start_date}/{@code end_date} 由后端筛选（A2 收口）：
     * 此前契约已声明、实现未接线 → 参数被静默忽略（运营筛选无效）。
     */
    @GetMapping("/orders/page")
    public Result<IPage<OrderVO>> pageAllOrders(@QueryModel OrderPageReq req) {
        return Result.ok(orderQueryManager.pageOrders(
                null, null, req.getStatus(), req.parseKeyword(),
                req.parseStartDateTime(), req.parseEndDateTime(), req.getPage(), req.getSize()));
    }

    /** 近 N 天订单趋势（图表数据；每日 {@code revenue} 为分）。 */
    @GetMapping("/stats/trend")
    public Result<List<OrderTrendVO>> trend(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(adminManager.orderTrend(days));
    }

    /** 销量 Top N 商品（V2.5 详细统计；{@code revenue} 为分）。 */
    @GetMapping("/stats/top-products")
    public Result<List<SpuStatVO>> topSpus(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(adminManager.topSpus(limit));
    }

    /** 指定日期区间分日销售额（V2.5 详细统计；每日 {@code revenue} 为分）。 */
    @GetMapping("/stats/sales")
    public Result<List<SalesByDateVO>> salesByDateRange(
            @RequestParam String from, @RequestParam String to) {
        return Result.ok(adminManager.saleByDateRange(java.time.LocalDate.parse(from), java.time.LocalDate.parse(to)));
    }
}
