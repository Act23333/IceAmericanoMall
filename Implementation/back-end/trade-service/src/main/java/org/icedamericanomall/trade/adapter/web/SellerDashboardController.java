package org.icedamericanomall.trade.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.adapter.response.SellerDashboardVO;
import org.icedamericanomall.trade.adapter.response.SpuStatVO;
import org.icedamericanomall.trade.app.dashboard.SellerDashboardManager;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Seller dashboard — simple statistics for the seller view.
 * 聚合逻辑下沉至 {@link SellerDashboardManager}。
 *
 * <p><b>批次 D（{@code Result<Map>} → 类型化 VO）</b>：两个端点的出参由
 * {@code Result<Map<String,Object>>} / {@code Result<List<Map<String,Object>>>} 改为
 * {@link SellerDashboardVO} / {@code List<SpuStatVO>}，Controller 只做身份解析与
 * {@code Result} 包装（不含任何装配逻辑，装配在 Manager）。数值口径见各 VO 字段 javadoc。
 */
@RestController
@PreAuthorize("@ss.hasPermi('seller:admin')")
@RequestMapping("/api/trade/seller/dashboard")
@RequiredArgsConstructor
public class SellerDashboardController {

    private final SellerDashboardManager sellerDashboardManager;

    /** S10 商家看板：今日订单数、待发货数、本月营收（分）。 */
    @GetMapping
    public Result<SellerDashboardVO> dashboard() {
        return Result.ok(sellerDashboardManager.dashboard(UserContext.getUserId()));
    }

    /** S11 V2.5 商品维度销量/金额统计（最近 N 天；{@code revenue} 为分）。 */
    @GetMapping("/product-stats")
    public Result<List<SpuStatVO>> spuStats(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(sellerDashboardManager.spuStats(UserContext.getUserId(), days));
    }
}
