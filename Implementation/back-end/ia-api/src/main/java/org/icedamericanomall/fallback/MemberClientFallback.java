package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.MemberClient;
import org.icedamericanomall.dto.MemberGrowthCO;
import org.icedamericanomall.dto.MemberLevelCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 会员等级/成长值降级工厂 —— <b>fail-open</b>，属 ADR-011 的 <b>① 非判别型辅助查询/非关键命令</b>。
 *
 * <ul>
 *   <li>{@code getLevel} 降级返回默认 NORMAL（折扣率 10000=不折扣、积分倍率 100=1 倍）并告警：
 *       方向是用户少得优惠（体验损失、可补偿），不是平台资损，下单可用性优先（ADR-044）；</li>
 *   <li>{@code recordGrowth} 降级返回 {@code null} 并告警：成长值入账失败不阻断交易主流程
 *       （与触达接线 ADR-042 同口径），Phase 1 不做补账任务。</li>
 * </ul>
 */
@Slf4j
@Component
public class MemberClientFallback implements FallbackFactory<MemberClient> {

    /** 降级默认口径：NORMAL / 不折扣 / 1 倍积分（与 seed 的 NORMAL 行一致）。 */
    static final MemberLevelCO DEFAULT_NORMAL =
            new MemberLevelCO("NORMAL", 10000, 100);

    @Override
    public MemberClient create(Throwable cause) {
        return new MemberClient() {

            @Override
            public MemberLevelCO getLevel(Long userId) {
                log.warn("会员等级查询降级：user-service 不可用，按默认无折扣计价 userId={}", userId, cause);
                return DEFAULT_NORMAL;
            }

            @Override
            public MemberLevelCO recordGrowth(MemberGrowthCO growth) {
                log.warn("会员成长值入账降级：user-service 不可用，本单成长值未入账（Phase 1 不补账）"
                        + " userId={}, orderNo={}, payAmount={}",
                        growth == null ? null : growth.getUserId(),
                        growth == null ? null : growth.getOrderNo(),
                        growth == null ? null : growth.getPayAmount(), cause);
                return null;
            }
        };
    }
}
