package org.icedamericanomall.pay.support;

import org.icedamericanomall.pay.domain.enums.RefundStatusEnum;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.domain.repository.RefundOrderRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link RefundOrderRepository} 的内存实现（<b>测试替身</b>）—— 与
 * {@link InMemoryPayOrderRepository} 同理：按列语义真实落库，
 * 回调侧只有用 {@code out_refund_no} 才查得到行（用内部 {@code refund_no} 一律查不到）。
 *
 * <p>{@code refund_no} / {@code out_refund_no} 均由生产代码的聚合工厂
 * （{@link RefundOrder#newProcessing}）生成，测试不手工填值 —— 否则「两个号是否真的分离」
 * 就成了测试自己的假设，而非被测代码的事实。
 */
public class InMemoryRefundOrderRepository implements RefundOrderRepository {

    private final Map<Long, RefundOrder> rows = new LinkedHashMap<>();

    private long sequence = 0L;

    @Override
    public RefundOrder getByRefundNo(String refundNo) {
        if (refundNo == null) {
            return null;
        }
        return rows.values().stream()
                .filter(row -> refundNo.equals(row.getRefundNo()))
                .findFirst().orElse(null);
    }

    @Override
    public RefundOrder getByOutRefundNo(String outRefundNo) {
        if (outRefundNo == null || outRefundNo.isBlank()) {
            return null;
        }
        return rows.values().stream()
                .filter(row -> outRefundNo.equals(row.getOutRefundNo()))
                .findFirst().orElse(null);
    }

    @Override
    public RefundOrder getByAfterSaleId(Long afterSaleId) {
        if (afterSaleId == null) {
            return null;
        }
        return rows.values().stream()
                .filter(row -> afterSaleId.equals(row.getAfterSaleId()))
                .findFirst().orElse(null);
    }

    @Override
    public RefundOrder getByBizRef(String bizRef) {
        if (bizRef == null) {
            return null;
        }
        return rows.values().stream()
                .filter(row -> bizRef.equals(row.getBizRef()))
                .findFirst().orElse(null);
    }

    @Override
    public RefundOrder createProcessing(Long afterSaleId, String orderNo, String payOrderNo, Long userId,
                                        String channel, int refundAmount) {
        RefundOrder row = RefundOrder.newProcessing(afterSaleId, orderNo, payOrderNo, userId, channel, refundAmount);
        row.setId(++sequence);
        rows.put(row.getId(), row);
        return row;
    }

    @Override
    public RefundOrder createOrderRefundProcessing(String orderNo, String payOrderNo, Long userId,
                                                   String channel, int refundAmount, String bizRef) {
        RefundOrder row = RefundOrder.newOrderProcessing(orderNo, payOrderNo, userId, channel, refundAmount, bizRef);
        row.setId(++sequence);
        rows.put(row.getId(), row);
        return row;
    }

    @Override
    public boolean markSuccessIfProcessing(String refundNo, String channelRefundNo) {
        RefundOrder row = getByRefundNo(refundNo);
        if (row == null || !row.isProcessing()) {
            return false;
        }
        row.setStatus(RefundStatusEnum.SUCCESS.getCode());
        row.setChannelRefundNo(channelRefundNo);
        row.setFailReason(null);
        return true;
    }

    @Override
    public boolean markFailedIfProcessing(String refundNo, String failReason) {
        RefundOrder row = getByRefundNo(refundNo);
        if (row == null || !row.isProcessing()) {
            return false;
        }
        row.setStatus(RefundStatusEnum.FAILED.getCode());
        row.setFailReason(failReason);
        return true;
    }

    @Override
    public boolean markProcessingIfFailed(String refundNo) {
        RefundOrder row = getByRefundNo(refundNo);
        if (row == null || !row.isFailed()) {
            return false;
        }
        row.setStatus(RefundStatusEnum.PROCESSING.getCode());
        row.setFailReason(null);
        return true;
    }

    @Override
    public List<RefundOrder> listStaleProcessing(LocalDateTime threshold, int limit) {
        return rows.values().stream()
                .filter(RefundOrder::isProcessing)
                .sorted(Comparator.comparing(RefundOrder::getId))
                .limit(limit)
                .toList();
    }

    /** 当前落库行数。 */
    public int rowCount() {
        return rows.size();
    }
}
