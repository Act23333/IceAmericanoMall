package org.icedamericanomall.pay.domain.service;

import org.icedamericanomall.pay.domain.enums.ReconciliationResultEnum;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationDetail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道对账比对纯函数（F4 渠道对账）—— <b>无任何框架依赖</b>（不引 Spring / MyBatis / Feign /
 * ia-integration），可在单元测试中直接 {@code new} / 静态调用（形态与 trade
 * {@code PostageCalculator} 一致）。
 *
 * <h2>比对口径（与 05-API-Specification §4.13.14 契约同步）</h2>
 * <ol>
 *   <li><b>比对键 = 对外渠道号</b>（{@code pay_order.channel_trade_no}）：渠道账单的「商户订单号」
 *       列就是本平台发起支付时传给渠道的 {@code out_trade_no}，即渠道号 —— <b>不是</b>
 *       {@code pay_order_no}（内部键从不外发，账单里不可能出现）；</li>
 *   <li><b>四态判定</b>：本地单在账单中缺失 → 3-本地有渠道无；账单单在本地缺失 →
 *       2-渠道有本地无；双方都有且金额不等 → 4-金额不一致；相等 → 1-匹配；</li>
 *   <li><b>record-only</b>：只产出差异明细供管理端查看，<b>不自动调账</b>（ADR-036）。</li>
 * </ol>
 *
 * <p><b>明细两侧标识</b>：命中本地单的明细同时回填 {@code payOrderNo}（内部定位用）与
 * {@code channelTradeNo}（与账单核对用）；只有渠道有的明细<b>没有</b>本地支付单号（它为
 * {@code null}），仅回填渠道号 —— 这类差异正是「渠道侧有、本地未落账」，不应伪造本地单号。
 *
 * <p><b>调用方职责</b>：渠道账单条目（ia-integration {@code ChannelBillItem}）属外部模型，
 * 由应用层先转换为 {@link BillEntry}（防腐层职责），本类不感知任何渠道 SDK 类型。
 *
 * <p><b>金额单位</b>：一律为「分」（{@code Integer}）；{@code null} 金额按 0 计入总额，
 * 但明细中保留 {@code null} 供管理端核对缺失侧。
 */
public final class ReconciliationComparer {

    private ReconciliationComparer() {
    }

    /**
     * 渠道账单条目（领域侧入参）—— 应用层从渠道 SDK 账单项转换而来的最小字段集。
     *
     * @param channelTradeNo 商户订单号（比对键，即本平台对外渠道号 {@code pay_order.channel_trade_no}）
     * @param channelTxnId   渠道交易号（渠道有此单时回填明细）
     * @param amount         账单金额（分，可 {@code null}）
     */
    public record BillEntry(String channelTradeNo, String channelTxnId, Integer amount) {
    }

    /**
     * 比对产出。
     *
     * @param details      四态明细（{@code batchId} 由仓储落库时回填）
     * @param channelTotal 渠道账单总额（分）
     * @param localTotal   本地支付成功总额（分）
     */
    public record Diff(List<ReconciliationDetail> details, int channelTotal, int localTotal) {
    }

    /**
     * 本地流水 ↔ 渠道账单比对（按对外渠道号匹配），产出四态明细与两侧总额。
     *
     * @param localOrders 本地支付成功流水（须含 {@code channelTradeNo}）
     * @param billEntries 渠道账单条目（已由应用层完成防腐转换）
     * @return 比对结果（明细 + 渠道/本地总额）
     */
    public static Diff compare(List<PayOrder> localOrders, List<BillEntry> billEntries) {
        Map<String, BillEntry> channelByTradeNo = new LinkedHashMap<>();
        for (BillEntry entry : billEntries) {
            if (entry.channelTradeNo() != null) {
                channelByTradeNo.putIfAbsent(entry.channelTradeNo(), entry);
            }
        }
        List<ReconciliationDetail> details = new ArrayList<>(billEntries.size() + localOrders.size());
        for (PayOrder local : localOrders) {
            BillEntry entry = channelByTradeNo.remove(local.getChannelTradeNo());
            details.add(buildDetail(local.getPayOrderNo(), local.getChannelTradeNo(), entry, local.getAmount()));
        }
        for (BillEntry entry : channelByTradeNo.values()) {
            details.add(buildDetail(null, entry.channelTradeNo(), entry, null));
        }
        return new Diff(details, sumChannel(billEntries), sumLocal(localOrders));
    }

    /** 组装一条明细（{@code entry == null} → 本地有渠道无；{@code localAmount == null} → 渠道有本地无）。 */
    private static ReconciliationDetail buildDetail(String payOrderNo, String channelTradeNo, BillEntry entry,
                                                    Integer localAmount) {
        Integer channelAmount = entry == null ? null : entry.amount();
        ReconciliationDetail detail = new ReconciliationDetail();
        detail.setPayOrderNo(payOrderNo);
        detail.setChannelTradeNo(channelTradeNo);
        detail.setChannelTxnId(entry == null ? null : entry.channelTxnId());
        detail.setChannelAmount(channelAmount);
        detail.setLocalAmount(localAmount);
        detail.setResult(resolveResult(localAmount, channelAmount).getCode());
        return detail;
    }

    /** 四态判定。 */
    private static ReconciliationResultEnum resolveResult(Integer localAmount, Integer channelAmount) {
        if (localAmount == null) {
            return ReconciliationResultEnum.CHANNEL_ONLY;
        }
        if (channelAmount == null) {
            return ReconciliationResultEnum.LOCAL_ONLY;
        }
        return localAmount.equals(channelAmount)
                ? ReconciliationResultEnum.MATCHED
                : ReconciliationResultEnum.AMOUNT_MISMATCH;
    }

    /** 渠道账单总额（分，{@code null} 金额按 0 计）。 */
    private static int sumChannel(List<BillEntry> billEntries) {
        return billEntries.stream()
                .mapToInt(entry -> entry.amount() == null ? 0 : entry.amount())
                .sum();
    }

    /** 本地支付成功总额（分，{@code null} 金额按 0 计）。 */
    private static int sumLocal(List<PayOrder> localOrders) {
        return localOrders.stream()
                .mapToInt(order -> order.getAmount() == null ? 0 : order.getAmount())
                .sum();
    }
}
