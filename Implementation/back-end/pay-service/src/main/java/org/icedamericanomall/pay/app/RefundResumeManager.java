package org.icedamericanomall.pay.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.integration.payment.ChannelRefundQueryResult;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.icedamericanomall.pay.domain.repository.RefundOrderRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 悬挂退款补偿编排（ADR-035「后果/负面风险」第 3 项收敛）—— 主动收敛「处理中」超时未终态的退款单。
 *
 * <p><b>问题背景</b>：微信渠道退款受理后 settled=false，终态只靠 PAY6 回调推进；回调丢失/未达时
 * 退款单永久停留处理中，而管理端 AD43 只允许「失败」单重试，处理中单没有任何收敛路径。
 * 本用例族与 PAY6 回调互为冗余（大厂标准解法：渠道查询 + 状态收敛），与 AD43 互补
 * （补偿置失败的单一经管理端重试收敛）。
 *
 * <p><b>事务纪律</b>：与 {@link RefundManager} 一致 —— 渠道查询/重放与售后回写都是 Feign，
 * 不加 {@code @Transactional}；状态迁移全部下沉为仓储条件更新（仅处理中可迁移），重复调度安全。
 *
 * <pre>
 * Scenario: 微信渠道悬挂单，渠道查询 SUCCESS
 *   Then 条件置成功 + I42 回写售后单（复用 RefundManager 共享结算入口，与 PAY6 同一落账路径）
 * Scenario: 微信渠道悬挂单，渠道查询 FAILED（CLOSED/ABNORMAL 归一化）
 *   Then 置失败（failReason 记录归一化状态），AD43 重试即可收敛（重试复用同号幂等）
 * Scenario: 微信渠道悬挂单，渠道查询 PROCESSING
 *   Then 本轮跳过（渠道仍在处理），下轮再看
 * Scenario: 余额/支付宝渠道悬挂单
 *   Then 重新发起渠道退款（天然幂等：余额 transaction_no=refundNo 重放；支付宝 Mock 同步成功）
 *        → 同步终态 → 条件置成功 + 回写
 * Scenario: 未知渠道悬挂单
 *   Then 告警跳过，不自动置失败（避免误推翻，需人工介入）
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundResumeManager {

    /**
     * 悬挂判定阈值（分钟）：退款单创建时间早于「当前时间 - 阈值」才算悬挂。
     * 取值理由：微信退款渠道侧通常分钟级出终态，10 分钟既显著大于正常处理窗口 +
     * PAY6 回调到达延迟（避免与在途回调竞争收敛），又把用户可见的悬挂时长控制在可接受范围。
     */
    static final int STALE_THRESHOLD_MINUTES = 10;

    /**
     * 单轮最大处理条数：限制单轮渠道查询/重放的总耗时（按单笔 ~200ms 估算约 20s），
     * 远低于常规分钟级调度间隔，避免任务重叠堆积；积压靠多轮滚动消化（升序老单优先）。
     */
    static final int BATCH_LIMIT = 100;

    private final RefundOrderRepository refundOrderService;
    private final PayOrderRepository payOrderService;
    private final PaymentChannelRouter channelRouter;
    private final RefundManager refundManager;

    /**
     * 补偿一轮悬挂退款单：批量逐单处理，单笔失败仅告警、不中断整批。
     *
     * <p>单笔异常（渠道查询失败 / 重放失败等）时退款单保持「处理中」，由下轮补偿再试 ——
     * 渠道侧状态未知时不轻易置失败（区别于发起路径的 fail-closed），也不会误伤在途回调。
     *
     * @return 本轮扫描到的悬挂退款单数量（供调度侧观测）
     */
    public int resumeStuckRefunds() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        List<RefundOrder> staleRefunds = refundOrderService.listStaleProcessing(threshold, BATCH_LIMIT);
        log.info("[refund-resume][scan] threshold={}, limit={}, staleCount={}",
                threshold, BATCH_LIMIT, staleRefunds.size());
        for (RefundOrder refundOrder : staleRefunds) {
            try {
                resumeOne(refundOrder);
            } catch (RuntimeException e) {
                log.error("[refund-resume][failed] refundNo={} 单笔补偿异常，保持处理中待下轮，继续下一单",
                        refundOrder.getRefundNo(), e);
            }
        }
        return staleRefunds.size();
    }

    /** 单笔补偿：按渠道分流 —— 微信走渠道查询确认终态，余额/支付宝走幂等重放，未知渠道告警跳过。 */
    void resumeOne(RefundOrder refundOrder) {
        PayChannelEnum channel;
        try {
            channel = PayChannelEnum.of(refundOrder.getChannel());
        } catch (BadRequestException e) {
            log.error("[refund-resume][skip] refundNo={} 未知渠道={}，不自动置失败，需人工介入",
                    refundOrder.getRefundNo(), refundOrder.getChannel());
            return;
        }
        switch (channel) {
            case WECHAT -> resumeWechat(refundOrder);
            case BALANCE, ALIPAY -> resumeByReplay(refundOrder);
        }
    }

    /**
     * 微信渠道补偿：按<b>对外退款项号</b>查渠道退款终态并收敛本地状态。
     *
     * <p>查询异常向上抛出（由批量循环兜底告警），本地状态不动 —— 查询不到结果不等于退款失败。
     */
    private void resumeWechat(RefundOrder refundOrder) {
        String refundNo = refundOrder.getRefundNo();
        String outRefundNo = refundOrder.getOutRefundNo();
        ChannelRefundQueryResult result = channelRouter.queryWechatRefund(outRefundNo);
        switch (result.state()) {
            case SUCCESS -> refundManager.markSuccessAndNotify(refundNo, result.refundId());
            case FAILED -> {
                String failReason = "渠道退款查询终止: " + result.state() + " refundId=" + result.refundId();
                refundManager.markFailedWithReason(refundNo, failReason);
                log.warn("[refund-resume][channel-failed] outRefundNo={} 渠道退款已终止，置失败待 AD43 重试: {}",
                        outRefundNo, failReason);
            }
            case PROCESSING -> log.debug("[refund-resume][skip] outRefundNo={} 渠道仍在处理中，本轮跳过",
                    outRefundNo);
        }
    }

    /**
     * 余额/支付宝渠道补偿：重新发起渠道退款（幂等重放）→ 同步终态结算。
     * 支付单缺失（异常数据）置失败让 AD43 可见，避免每轮无效重放。
     */
    private void resumeByReplay(RefundOrder refundOrder) {
        PayOrder payOrder = payOrderService.getByPayOrderNo(refundOrder.getPayOrderNo());
        if (payOrder == null) {
            log.error("[refund-resume][failed] refundNo={} 支付单不存在: {}，置失败待 AD43 重试",
                    refundOrder.getRefundNo(), refundOrder.getPayOrderNo());
            refundManager.markFailedWithReason(refundOrder.getRefundNo(), "支付单不存在");
            return;
        }
        refundManager.dispatchAndSettle(refundOrder, payOrder, null);
    }
}
