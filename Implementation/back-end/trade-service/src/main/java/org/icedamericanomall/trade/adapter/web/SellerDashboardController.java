package org.icedamericanomall.trade.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.app.dashboard.SellerDashboardManager;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Seller dashboard — simple statistics for the seller view.
 * 聚合逻辑下沉至 {@link SellerDashboardManager}。
 */
@RestController
@PreAuthorize("@ss.hasPermi('seller:admin')")
@RequestMapping("/api/trade/seller/dashboard")
@RequiredArgsConstructor
public class SellerDashboardController {

    private final SellerDashboardManager sellerDashboardManager;

    @GetMapping
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(sellerDashboardManager.dashboard(UserContext.getUserId()));
    }

    /** V2.5 商品维度销量/金额统计（最近 N 天）。 */
    @GetMapping("/product-stats")
    public Result<List<Map<String, Object>>> spuStats(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(sellerDashboardManager.spuStats(UserContext.getUserId(), days));
    }
}
