package org.icedamericanomall.pay.domain.model.refundorder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.pay.domain.enums.RefundStatusEnum;

import java.time.LocalDateTime;
import org.icedamericanomall.common.utils.IdGenerator;

/**
 * 退款单聚合根（纯 POJO，零框架依赖）—— {@code refund_order} 表的领域模型（F1 买家退款资金流）。
 *
 * <pre>
 * 状态机 (统一见 {@link RefundStatusEnum}):
 *   0-PROCESSING → 1-SUCCESS
 *                ↘ 2-FAILED ──(管理端 AD43 重试)──→ 0-PROCESSING
 *
 * 不变量:
 *   1. biz_ref 唯一（uk_biz_ref）—— 统一幂等业务键：售后=AS:{afterSaleId}，拼团=GB:{orderNo}（ADR-049），
 *      退款发起的幂等由该唯一约束兜底
 *   2. after_sale_id 唯一且可空（uk_after_sale）—— 售后场景一售后单一次退款；无售后场景（拼团，I55）为 null
 *   3. refund_no 唯一（uk_refund_no）—— 对外定位键与管理端重试定位键
 *   4. 成功迁移只在「处理中」态发生（条件更新），保证回调重复幂等
 *   5. 售后单状态仅在退款成功后回写（PROCESSING / FAILED 时停留 APPROVED）；无售后单退款跳过回写
 * </pre>
 *
 * <p><b>与 {@code PayOrder} 的聚合边界</b>：退款单与支付单是两个聚合，只通过
 * {@code payOrderNo} 单向引用（读支付单取渠道与金额），不互相持有对象引用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundOrder {

    /** 技术主键（集群内部口径，不对外暴露）。 */
    private Long id;

    /** 退款单号（业务唯一键，32 位 UUID；渠道侧即 {@code out_refund_no}，重试复用同号）。 */
    private String refundNo;

    /** 关联订单号（{@code orders.order_no}）。 */
    private String orderNo;

    /** 关联支付单号（{@code pay_order.pay_order_no}）。 */
    private String payOrderNo;

    /** 关联售后单 ID（唯一约束：一售后单一次退款）；无售后单退款（I55 拼团）为 {@code null}。 */
    private Long afterSaleId;

    /** 统一幂等业务键（uk_biz_ref）：售后 = {@code AS:{afterSaleId}}，拼团 = {@code GB:{orderNo}}（ADR-049）。 */
    private String bizRef;

    /** 买家用户 ID。 */
    private Long userId;

    /** 退款渠道（与原支付渠道一致，原路退回）：WECHAT / ALIPAY / BALANCE。 */
    private String channel;

    /** 退款金额（分；申请时已校验 ≤ 订单实付 payAmount）。 */
    private Integer refundAmount;

    /** 状态：0-处理中 / 1-成功 / 2-失败。 */
    private Integer status;

    /** 渠道退款单号（微信 refund_id 等，回调回填）。 */
    private String channelRefundNo;

    /** 失败原因（渠道返回或系统错误摘要，不含敏感细节）。 */
    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /** 统一幂等业务键的售后场景前缀（ADR-049）：售后退款 = {@code AS:{afterSaleId}}。 */
    public static final String BIZ_REF_AFTER_SALE_PREFIX = "AS:";

    /**
     * 售后退款场景的幂等业务键（{@code uk_biz_ref} 取值）。
     *
     * @param afterSaleId 售后单 ID
     * @return {@code AS:{afterSaleId}}
     */
    public static String afterSaleBizRef(long afterSaleId) {
        return BIZ_REF_AFTER_SALE_PREFIX + afterSaleId;
    }

    /**
     * 创建「处理中」退款单（未落库状态，{@code id} 由仓储回填）。
     *
     * <p>落库时 {@code uk_refund_no} / {@code uk_biz_ref}（售后场景即 {@code AS:{afterSaleId}}）是
     * 并发下的最终兜底：并发重复创建会抛 {@code DuplicateKeyException}，由应用层回落为「返回既有退款单」。
     *
     * @param afterSaleId  售后单 ID（幂等锚点）
     * @param orderNo      订单号
     * @param payOrderNo   支付单号
     * @param userId       买家用户 ID
     * @param channel      退款渠道（同原支付渠道）
     * @param refundAmount 退款金额（分）
     * @return 处理中的退款单（未落库）
     */
    public static RefundOrder newProcessing(Long afterSaleId, String orderNo, String payOrderNo, Long userId,
                                             String channel, int refundAmount) {
        return RefundOrder.builder()
                .refundNo(newBusinessId())
                .afterSaleId(afterSaleId)
                .bizRef(afterSaleBizRef(afterSaleId))
                .orderNo(orderNo)
                .payOrderNo(payOrderNo)
                .userId(userId)
                .channel(channel)
                .refundAmount(refundAmount)
                .status(RefundStatusEnum.PROCESSING.getCode())
                .build();
    }

    /**
     * 创建「处理中」的无售后单退款单（I55 按订单全额退款，ADR-049；未落库状态，{@code id} 由仓储回填）。
     *
     * <p>与 {@link #newProcessing} 并存的独立创建口径：{@code afterSaleId} 恒为 {@code null}，
     * 幂等锚点改由调用方传入的 {@code bizRef}（拼团场景 = {@code GB:{orderNo}}）承担，
     * {@code uk_biz_ref} 兜底并发幂等。
     *
     * @param orderNo      订单号
     * @param payOrderNo   支付单号
     * @param userId       买家用户 ID
     * @param channel      退款渠道（同原支付渠道）
     * @param refundAmount 退款金额（分，按订单实付全额）
     * @param bizRef       统一幂等业务键（uk_biz_ref，不可为空）
     * @return 处理中的退款单（未落库）
     */
    public static RefundOrder newOrderProcessing(String orderNo, String payOrderNo, Long userId,
                                                 String channel, int refundAmount, String bizRef) {
        return RefundOrder.builder()
                .refundNo(newBusinessId())
                .orderNo(orderNo)
                .payOrderNo(payOrderNo)
                .userId(userId)
                .channel(channel)
                .refundAmount(refundAmount)
                .bizRef(bizRef)
                .status(RefundStatusEnum.PROCESSING.getCode())
                .build();
    }

    /** 生成业务唯一键（32 位 UUID，无连字符；与存量 {@code refund_no} 取值格式保持一致）。 */
    public static String newBusinessId() {
        return IdGenerator.nextId();
    }

    // ==================== 状态谓词 ====================

    /** 是否处理中（回调置成功的前提态）。 */
    public boolean isProcessing() {
        return status != null && status == RefundStatusEnum.PROCESSING.getCode();
    }

    /** 是否已退款成功。 */
    public boolean isSuccess() {
        return status != null && status == RefundStatusEnum.SUCCESS.getCode();
    }

    /** 是否失败（管理端重试的前提态）。 */
    public boolean isFailed() {
        return status != null && status == RefundStatusEnum.FAILED.getCode();
    }

    /**
     * 断言当前退款单可重试（管理端 AD43 的前置不变量）。
     *
     * <p>只有「失败」单可重试；处理中/已成功一律拒绝 —— 状态码语义属本聚合，
     * 不允许应用层各自判断（否则新增状态时必然漏改一处）。
     *
     * @throws BizException 非失败状态（{@code REFUND_STATUS_INVALID}）
     */
    public void assertRetryable() {
        if (!isFailed()) {
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID, "当前退款状态不允许该操作");
        }
    }
}
