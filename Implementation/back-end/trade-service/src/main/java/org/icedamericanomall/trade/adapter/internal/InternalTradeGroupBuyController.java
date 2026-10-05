package org.icedamericanomall.trade.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.GroupTeamRefundCO;
import org.icedamericanomall.trade.app.order.GroupBuyParticipationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal group-buy endpoints for inter-service Feign calls (F13 拼团, ADR-049).
 * All endpoints return raw types without Result wrapping (internal API convention).
 */
@RestController
@RequestMapping("/internal/trade/group-buy")
@RequiredArgsConstructor
public class InternalTradeGroupBuyController {

    private final GroupBuyParticipationService groupBuyParticipationService;

    /**
     * I54 结团退款联动 —— marketing {@code GroupBuyExpireJob} 把超时团置已失败后调用：
     * 逐单「条件取消(2→5) + 恢复库存 + I55 全额退款」，单笔失败 try/catch 继续，不中断整团
     * （fail-open 触发器：调用失败仅告警，ADR-049）。
     */
    @PostMapping("/refund-team")
    public void refundTeam(@RequestBody GroupTeamRefundCO refund) {
        groupBuyParticipationService.refundFailedTeam(refund);
    }
}
