package org.icedamericanomall.client;

import org.icedamericanomall.dto.GroupTeamRefundCO;
import org.icedamericanomall.fallback.GroupBuyTeamClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 结团退款联动 Feign 客户端（F13 拼团：marketing-service → trade-service，契约 §4.14.16b I54，V5.14）。
 *
 * <p>调用方：marketing {@code GroupBuyExpireJob}（团超时置已失败后）。
 *
 * <p><b>降级语义：fail-open + 告警</b>——结团事实已定（团状态已置失败），退款闭环由 trade
 * 内部逐单 try/catch 收敛；调用失败仅告警（失败单依赖 refund_order 失败态 + 补偿挂账 roadmap，
 * ADR-049），与资金命令 I55 的 fail-closed 刻意不同（本调用是「触发器」，不是资金命令本身）。
 */
@FeignClient(name = "trade-service", path = "/internal/trade/group-buy",
        contextId = "groupBuyTeam", fallbackFactory = GroupBuyTeamClientFallback.class)
public interface GroupBuyTeamClient {

    /** 团失败后逐单触发「取消 + 恢复库存 + 全额退款」。 */
    @PostMapping("/refund-team")
    void refundTeam(@RequestBody GroupTeamRefundCO refund);
}
