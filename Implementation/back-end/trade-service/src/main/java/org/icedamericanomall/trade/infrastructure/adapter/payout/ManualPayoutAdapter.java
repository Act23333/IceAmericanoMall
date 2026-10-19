package org.icedamericanomall.trade.infrastructure.adapter.payout;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort;
import org.springframework.stereotype.Component;

/**
 * 「人工确认」出款适配器 —— <b>本地确认，不做任何远程调用</b>。
 *
 * <p>适用场景：钱由财务在系统外（网银/柜台）转出，系统只登记「已打款」。
 * 因此它可以在数据库事务内调用（没有 I/O）；<b>接真实渠道时必须换掉它</b>，且换成两阶段：
 * 见 {@link WithdrawalPayoutPort} 类注释与 {@code open-items} §1.35 —— 远程出款不能放进事务。
 *
 * <p>它仍然返回 {@code channelRef = MANUAL}：对账时能一眼看出这些「打款」没有渠道回执，
 * 不能与真实渠道流水混为一谈。
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "trade.withdrawal.payout-mock.enabled", havingValue = "false", matchIfMissing = true)
public class ManualPayoutAdapter implements WithdrawalPayoutPort {

    /** 人工确认的渠道标识；对账时据此把这些单排除在「渠道流水核对」之外。 */
    static final String CHANNEL_REF = "MANUAL";

    @Override
    public PayoutResult pay(PayoutCommand command) {
        // 刻意不打印账户全量与开户行：日志也是泄露面
        log.info("[payout] 人工出款确认 payoutNo={} withdrawalId={} amount={}（无渠道回执）",
                command.payoutNo(), command.withdrawalId(), command.amount());
        return new PayoutResult(true, CHANNEL_REF, null);
    }
}
