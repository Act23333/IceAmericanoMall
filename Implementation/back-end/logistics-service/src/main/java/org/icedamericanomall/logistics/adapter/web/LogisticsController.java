package org.icedamericanomall.logistics.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.logistics.app.LogisticsManager;
import org.icedamericanomall.logistics.adapter.response.LogisticsVO;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对外物流查询接口 —— 仅做参数接收与 Result 包装，归属校验与装配在 {@link LogisticsManager}。
 *
 * <p><b>V5.6 破坏性变更（F3 拆单发货）</b>：一订单多包裹后，本端点响应由<b>单对象</b>
 * 改为<b>列表</b>（按 {@code packageNo} 升序），见契约 §4.8.1。
 */
@RestController
@RequestMapping("/api/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final LogisticsManager logisticsManager;

    /**
     * 查询订单全部包裹物流（仅订单所属用户可查，否则 FORBIDDEN）。
     *
     * @param orderId 订单 ID
     * @return 包裹物流列表（按包裹号升序；无包裹时空列表）
     */
    @GetMapping("/{orderId}")
    public Result<List<LogisticsVO>> get(@PathVariable Long orderId) {
        return Result.ok(logisticsManager.getOwnedLogistics(orderId, UserContext.getUserId()));
    }
}
