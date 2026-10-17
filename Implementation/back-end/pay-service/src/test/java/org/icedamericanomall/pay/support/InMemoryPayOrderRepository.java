package org.icedamericanomall.pay.support;

import org.icedamericanomall.pay.domain.enums.PayStatusEnum;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link PayOrderRepository} 的内存实现（<b>测试替身</b>）—— 在无数据库的前提下打通
 * 「发起 → 落库 → 渠道回调 → 查单」的真实语义链路。
 *
 * <p><b>为什么必须用内存仓储而不是 Mockito 打桩</b>：Mockito 仓储可以「按测试想让它返回什么就返回什么」，
 * 因此能轻易掩盖「发起侧与回调侧用了两个不同的键」这类缺陷 —— 历史单测正是以
 * {@code Map.of("out_trade_no", "PAY-1")} + 桩仓储把该路径糊过去的。本实现按<b>列语义</b>真实落库：
 * 回调方必须用 {@code channel_trade_no} 才查得到行，用 {@code pay_order_no} / {@code order_no}
 * 一律查不到，与 {@code SELECT ... WHERE channel_trade_no = ? } 同口径。
 *
 * <p>语义按仓储契约实现：{@link #resetPending} / {@link #attachPayUrl} / {@link #markSuccessIfPending}
 * 都是「条件更新」—— 只有命中期望状态才算成功，未命中返回 {@code false}/{@code null}。
 */
public class InMemoryPayOrderRepository implements PayOrderRepository {

    private final Map<Long, PayOrder> rows = new LinkedHashMap<>();

    private long sequence = 0L;

    @Override
    public PayOrder getByBizOrderNo(String bizOrderNo) {
        return rows.values().stream()
                .filter(row -> bizOrderNo != null && bizOrderNo.equals(row.getBizOrderNo()))
                .max(Comparator.comparing(PayOrder::getId))
                .orElse(null);
    }

    @Override
    public PayOrder getByPayOrderNo(String payOrderNo) {
        if (payOrderNo == null) {
            return null;
        }
        return rows.values().stream()
                .filter(row -> payOrderNo.equals(row.getPayOrderNo()))
                .findFirst().orElse(null);
    }

    @Override
    public PayOrder getByChannelTradeNo(String channelTradeNo) {
        if (channelTradeNo == null || channelTradeNo.isBlank()) {
            return null;
        }
        return rows.values().stream()
                .filter(row -> channelTradeNo.equals(row.getChannelTradeNo()))
                .findFirst().orElse(null);
    }

    @Override
    public PayOrder createPending(String bizOrderNo, Long userId, int amount, String channelCode, int payType) {
        PayOrder row = PayOrder.newPending(bizOrderNo, userId, amount, channelCode, payType);
        row.setId(++sequence);
        rows.put(row.getId(), row);
        return row;
    }

    @Override
    public PayOrder resetPending(PayOrder existing, int amount, String channelCode, int payType) {
        PayOrder row = rows.get(existing.getId());
        if (row == null || !row.isPendingPay()) {
            return null;
        }
        row.applyPendingReset(amount, channelCode, payType,
                LocalDateTime.now().plusMinutes(PayOrder.PAY_TIMEOUT_MINUTES));
        return row;
    }

    @Override
    public boolean attachPayUrl(PayOrder payOrder, String payUrl) {
        PayOrder row = rows.get(payOrder.getId());
        if (row == null || !row.isPendingPay()) {
            return false;
        }
        row.applyPayUrl(payUrl);
        return true;
    }

    @Override
    public boolean markSuccessIfPending(String payOrderNo, String resultCode) {
        PayOrder row = getByPayOrderNo(payOrderNo);
        if (row == null || !row.isPendingPay()) {
            return false;
        }
        row.setStatus(PayStatusEnum.SUCCESS.getCode());
        row.setResultCode(resultCode);
        row.setPaySuccessTime(LocalDateTime.now());
        return true;
    }

    @Override
    public boolean markBalancePaidIfPending(Long payOrderId) {
        PayOrder row = rows.get(payOrderId);
        if (row == null || !row.isPendingPay()) {
            return false;
        }
        row.setStatus(PayStatusEnum.SUCCESS.getCode());
        row.setPaySuccessTime(LocalDateTime.now());
        return true;
    }

    @Override
    public boolean cancelPending(Long payOrderId) {
        PayOrder row = rows.get(payOrderId);
        if (row == null || !row.isPendingPay()) {
            return false;
        }
        row.setStatus(PayStatusEnum.TIMEOUT_CANCEL.getCode());
        return true;
    }

    @Override
    public List<PayOrder> listTimeout(LocalDateTime cutoff) {
        return rows.values().stream()
                .filter(row -> row.isPendingPay() && row.getPayOverTime() != null
                        && row.getPayOverTime().isBefore(cutoff))
                .toList();
    }

    @Override
    public List<PayOrder> listPaidByDateAndChannel(LocalDate billDate, String channel) {
        return rows.values().stream()
                .filter(PayOrder::isPaid)
                .filter(row -> channel == null || channel.equals(row.getPayChannelCode()))
                .toList();
    }

    /** 当前落库行数（断言「复用而不是新增」用）。 */
    public int rowCount() {
        return rows.size();
    }
}
