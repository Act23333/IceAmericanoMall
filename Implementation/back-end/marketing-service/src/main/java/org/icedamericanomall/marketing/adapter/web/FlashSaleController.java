package org.icedamericanomall.marketing.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.marketing.adapter.response.FlashBuyVO;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
import org.icedamericanomall.marketing.app.MarketingManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 秒杀端点（适配层）—— 场次列表 + 抢购。
 *
 * <p>{@code POST /api/flash/buy} 走完整编排链：Controller → Manager（Redis 预扣 + Feign 落单 + MQ 统计）；
 * 控制器只做参数接收与结果透传。
 *
 * <p><b>归位说明</b>：适配层子包按受众分组（web/admin/internal），本类为前台/公开入口。
 */
@RestController
@RequestMapping("/api/flash")
@RequiredArgsConstructor
public class FlashSaleController {

    private final MarketingManager marketingManager;

    /** 进行中的秒杀场次列表。 */
    @GetMapping
    public Result<List<FlashSaleVO>> list() {
        return Result.ok(marketingManager.listActiveFlashSales());
    }

    /** 秒杀抢购：Redis 预扣 → trade-service 落单 → 返回真实订单号。 */
    @PostMapping("/buy")
    public Result<FlashBuyVO> buy(@RequestParam(name = "flash_id") Long flashId, @RequestParam(name = "address_id") Long addressId) {
        return Result.ok(marketingManager.buyFlashSale(flashId, addressId));
    }
}
