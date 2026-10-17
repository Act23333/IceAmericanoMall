package org.icedamericanomall.pay.domain.repository;

import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款单仓储（领域端口）—— 实现方：{@code infrastructure.persistence.RefundOrderRepositoryImpl}。
 *
 * <p>状态迁移一律条件更新（WHERE 带期望状态），保证回调/重试并发下只有一个生效。
 */
public interface RefundOrderRepository {

    /** 按退款单号取（内部业务唯一键；管理端 AD43 重试 / 内部定位）。 */
    RefundOrder getByRefundNo(String refundNo);

    /**
     * 按对外退款项号取（{@code refund_order.out_refund_no}）—— <b>渠道退款回调 / 退款状态查询的
     * 唯一寻址入口</b>（渠道只认它，永远不认内部 {@code refund_no}）。
     *
     * @param outRefundNo 对外退款项号（微信 {@code out_refund_no}）
     * @return 退款单；不存在返回 {@code null}
     */
    RefundOrder getByOutRefundNo(String outRefundNo);

    /** 按售后单 ID 取（uk_after_sale 幂等锚点，售后场景）。 */
    RefundOrder getByAfterSaleId(Long afterSaleId);

    /** 按统一幂等业务键取（uk_biz_ref 幂等锚点：售后=AS:{id}，拼团=GB:{orderNo}，ADR-049）。 */
    RefundOrder getByBizRef(String bizRef);

    /** 新增「处理中」退款单（refund_no / 创建口径由聚合工厂内聚），返回回填 id 后的聚合。 */
    RefundOrder createProcessing(Long afterSaleId, String orderNo, String payOrderNo, Long userId,
                                 String channel, int refundAmount);

    /**
     * 新增「处理中」的无售后单退款单（I55 按订单全额退款，ADR-049）：
     * {@code after_sale_id = null} + {@code biz_ref} 幂等锚点落库，返回回填 id 后的聚合。
     */
    RefundOrder createOrderRefundProcessing(String orderNo, String payOrderNo, Long userId,
                                            String channel, int refundAmount, String bizRef);

    /** 回调/执行置成功（WHERE refundNo + status=处理中），回填渠道退款单号。 */
    boolean markSuccessIfProcessing(String refundNo, String channelRefundNo);

    /** 置失败（WHERE status=处理中），记录失败摘要。 */
    boolean markFailedIfProcessing(String refundNo, String failReason);

    /** 管理端重试：失败 → 处理中（WHERE status=失败）。 */
    boolean markProcessingIfFailed(String refundNo);

    /**
     * 悬挂退款补偿（ADR-035 风险收敛）：取创建时间早于 {@code threshold} 的「处理中」退款单，
     * 按创建时间升序至多 {@code limit} 条（老单优先收敛，单轮批量有界）。
     *
     * @param threshold 悬挂判定阈值（创建时间早于该时刻才算悬挂，避开在途渠道处理窗口）
     * @param limit     单轮最大处理条数
     * @return 悬挂的「处理中」退款单列表（可能为空）
     */
    List<RefundOrder> listStaleProcessing(LocalDateTime threshold, int limit);
}
