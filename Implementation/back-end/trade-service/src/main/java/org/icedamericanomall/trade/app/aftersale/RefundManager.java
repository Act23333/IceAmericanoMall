package org.icedamericanomall.trade.app.aftersale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.RefundClient;
import org.icedamericanomall.dto.RefundCreateCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.icedamericanomall.trade.domain.model.aftersale.AfterSale;
import org.icedamericanomall.trade.domain.repository.AfterSaleRepository;
import org.springframework.stereotype.Component;

/**
 * 退款编排 Manager（F1 买家退款资金流的 trade 侧）—— 售后用例族中「退款发起」的编排入口。
 *
 * <p><b>为何独立成 Manager 而不并入 {@code OrderLifecycleManager}（ADR-025 用例族划分）</b>：
 * 生命周期 Manager 负责「取消 / 收货 / 发货 / 超时」这些<b>订单状态机</b>用例；
 * 退款发起属<b>售后资金</b>用例族（读售后单 → 触发跨服务退款），职责与失败语义都不同
 * （订单类多为 fail-open 补偿，退款类是 fail-closed 资金命令）。独立后各自内聚、可独立演进。
 *
 * <p><b>事务纪律（硬约束）</b>：本类含 Feign 调用，<b>严禁</b>在事务内执行。
 * 因此调用链是「控制器 → {@code AfterSaleService.review}（本地事务，先提交）→
 * 本 Manager（事务外触发退款）」，绝不把 Feign 塞进 review 的事务里。
 *
 * <pre>
 * Scenario: 售后审核同意 → 触发退款
 *   Then 以售后单的 afterSaleId/orderNo/refundAmount 发起 I40；成功则记录退款单号与状态
 * Scenario: 退款发起失败
 *   Then 不吞异常（fail-closed）：售后单停留 APPROVED，异常上抛让管理端可见并重试
 * Scenario: 重复审核同意（幂等）
 *   Then I40 侧 uk_after_sale 兜底返回既有退款单，不重复退款
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundManager {

    private final AfterSaleRepository afterSaleRepository;
    private final RefundClient refundClient;

    /**
     * 触发退款（I40）—— 售后审核同意后调用。
     *
     * @param afterSaleId 售后单 ID
     * @throws RuntimeException 退款发起失败（含 Feign 降级 fail-closed 抛出的
     *                          {@code BizException}）—— 不吞异常，售后单停留 APPROVED 待重试
     */
    public void triggerRefund(Long afterSaleId) {
        AfterSale afterSale = afterSaleRepository.findById(afterSaleId);
        if (afterSale == null) {
            log.warn("售后单不存在，跳过退款触发: afterSaleId={}", afterSaleId);
            return;
        }
        RefundCreateCO request = new RefundCreateCO();
        request.setAfterSaleId(afterSale.getId());
        request.setOrderNo(afterSale.getOrderNo());
        request.setRefundAmount(afterSale.getRefundAmount());
        request.setReason(afterSale.getReason());
        try {
            RefundResultCO result = refundClient.refund(request);
            log.info("退款已发起: afterSaleId={}, orderNo={}, refundNo={}, status={}",
                    afterSaleId, afterSale.getOrderNo(),
                    result == null ? null : result.getRefundNo(),
                    result == null ? null : result.getStatus());
        } catch (Exception e) {
            log.error("退款发起失败（售后单停留 APPROVED，可由管理端重新审核同意或 AD43 重试）"
                    + ": afterSaleId={}, orderNo={}, refundAmount={}",
                    afterSaleId, afterSale.getOrderNo(), afterSale.getRefundAmount(), e);
            throw e;
        }
    }
}
