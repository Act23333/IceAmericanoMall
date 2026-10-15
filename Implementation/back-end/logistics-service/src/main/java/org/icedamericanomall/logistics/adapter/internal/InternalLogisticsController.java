package org.icedamericanomall.logistics.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.logistics.app.LogisticsManager;
import org.icedamericanomall.dto.CreateLogisticsCO;
import org.springframework.web.bind.annotation.*;

/**
 * Internal Feign endpoints — called by trade-service after order ships.
 * CO → Entity 的装配在 {@link org.icedamericanomall.logistics.adapter.assembler.LogisticsConverter}，Controller 不手写转换。
 */
@RestController
@RequestMapping("/internal/logistics")
@RequiredArgsConstructor
public class InternalLogisticsController {

    private final LogisticsManager logisticsManager;

    /**
     * Create logistics record after seller ships a package（同一 (订单, 包裹号) 重复事件幂等）。
     */
    @PostMapping("/create")
    public void createLogistics(@RequestBody CreateLogisticsCO dto) {
        logisticsManager.createLogistics(dto);
    }

    /**
     * Update one package's logistics status (called by scheduled task or courier callback).
     *
     * <p><b>V5.6（F3）</b>：一订单多包裹后需定位到具体包裹，新增 {@code packageNo} 参数；
     * 缺省 1（单包裹语义兼容）。
     */
    @PutMapping("/{orderId}/status")
    public void updateStatus(@PathVariable Long orderId,
                             @RequestParam Integer status,
                             @RequestParam(name = "package_no", required = false, defaultValue = "1") Integer packageNo) {
        logisticsManager.updateStatus(orderId, packageNo, status);
    }
}
