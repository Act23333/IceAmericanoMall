package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.GroupBuyTeamClient;
import org.icedamericanomall.dto.GroupTeamRefundCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 结团退款联动降级工厂 —— <b>fail-open + 告警</b>（触发器语义，见 {@link GroupBuyTeamClient} 类注释）。
 */
@Slf4j
@Component
public class GroupBuyTeamClientFallback implements FallbackFactory<GroupBuyTeamClient> {

    @Override
    public GroupBuyTeamClient create(Throwable cause) {
        return new GroupBuyTeamClient() {

            @Override
            public void refundTeam(GroupTeamRefundCO refund) {
                log.warn("结团退款联动降级：trade-service 不可用，本团退款未触发（补偿挂账 roadmap，ADR-049）"
                                + " teamNo={}, orderNos={}",
                        refund == null ? null : refund.getTeamNo(),
                        refund == null ? null : refund.getOrderNos(), cause);
            }
        };
    }
}
