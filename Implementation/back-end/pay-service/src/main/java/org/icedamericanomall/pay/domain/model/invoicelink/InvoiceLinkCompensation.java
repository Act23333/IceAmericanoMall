package org.icedamericanomall.pay.domain.model.invoicelink;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.icedamericanomall.pay.domain.enums.InvoiceLinkCompensationStatusEnum;
import org.icedamericanomall.pay.domain.enums.InvoiceLinkKindEnum;

import java.time.LocalDateTime;

/**
 * 发票联动补偿记录聚合根（纯 POJO，零框架依赖）—— {@code invoice_link_compensation} 表的领域模型。
 *
 * <p><b>存在理由</b>：退款成功后的发票联动（I56 全额冲红 / I60 部分退款置标，PRD D4）是跨服务 Feign
 * 调用，<b>退款优先</b>（PRD 口径不变）—— 联动失败绝不能阻断退款落账。但「失败只打日志」等于
 * <b>静默丢失</b>：退款已到买家账上，发票却仍是有效已开具状态，且无人知道丢了一次联动。
 * 本聚合把「丢失」变成「可补偿」：联动失败即落一条 {@link InvoiceLinkCompensationStatusEnum#PENDING}
 * 记录，由后续批次的重试任务 / 管理端人工收敛。
 *
 * <pre>
 * 不变量:
 *   1. (order_no, scene_ref, kind) 唯一（uk_order_scene_kind）—— 幂等锚点：同一退款场景的同一类联动
 *      重复失败只留一条记录，绝不重复堆积（重复触发由 DB 唯一键兜底，不依赖应用层先查后插）
 *   2. status 初态恒为 PENDING，retry_count 初值恒为 0（重试计数只由重试任务推进）
 *   3. next_retry_at 落库即「当前时间」—— 语义是「调度器就绪后即刻可拾取」，不做首发延迟
 *   4. last_error 有界（{@value #LAST_ERROR_MAX_LENGTH}，与列宽一致）—— 落库路径本身不因超长摘要而失败
 * </pre>
 *
 * <p><b>与 {@code RefundOrder} 的边界</b>：本聚合只按 {@code order_no} + {@code scene_ref}
 * （= {@code refund_order.biz_ref}）单向引用退款场景，不持有退款单对象引用；重试所需的重放参数
 * 完全由这两列 + {@code kind} 还原。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceLinkCompensation {

    /** {@code last_error} 列宽上限（VARCHAR(500)）：超长摘要截断，保证落库不会因长度失败。 */
    public static final int LAST_ERROR_MAX_LENGTH = 500;

    /** {@code scene_ref} 列宽上限（VARCHAR(64)）：超长场景号截断。 */
    public static final int SCENE_REF_MAX_LENGTH = 64;

    /** 技术主键（集群内部口径，不对外暴露）。 */
    private Long id;

    /** 关联订单号（{@code orders.order_no}，32 位 hex；落库为 BINARY(16)，ADR-050）。 */
    private String orderNo;

    /** 联动场景号（= {@code refund_order.biz_ref}：售后 {@code AS:{afterSaleId}}、拼团 {@code GB:{orderNo}}）。 */
    private String sceneRef;

    /** 补偿类型：1-VOID 全额冲红 / 2-PARTIAL 部分退款置标（见 {@link InvoiceLinkKindEnum}）。 */
    private Integer kind;

    /** 状态：0-PENDING / 1-DONE / 2-FAILED_PERMANENT（见 {@link InvoiceLinkCompensationStatusEnum}）。 */
    private Integer status;

    /** 已重试次数（调度器每次拾取重试 +1）。 */
    private Integer retryCount;

    /** 下次可拾取时间（落库即当前时间；重试失败按退避推迟）。 */
    private LocalDateTime nextRetryAt;

    /** 最近一次失败原因摘要（异常类型 + 消息，不含敏感细节）。 */
    private String lastError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 创建「待补偿」记录（未落库状态，{@code id} 由仓储回填）。
     *
     * <p><b>本方法不抛异常</b>：调用点位于退款主流程的异常兜底路径上，任何抛出都等于
     * 把「补偿记录写不进去」升级成「退款流程异常」。{@code orderNo} / {@code sceneRef} 的非空
     * 前置条件由应用层守卫（{@code PayEventNotifier#triggerInvoiceLinkage} 已在联动前判空）；
     * 超长值在此截断，与列宽对齐。
     *
     * @param orderNo   关联订单号（32 位 hex 业务键，非空）
     * @param sceneRef  联动场景号（= {@code refund_order.biz_ref}，非空）
     * @param kind      补偿类型（VOID / PARTIAL，由调用分支决定，不允许为空）
     * @param lastError 失败原因摘要（异常类型 + 消息，可空；超长截断至 {@value #LAST_ERROR_MAX_LENGTH}）
     * @return 待补偿记录（未落库）
     */
    public static InvoiceLinkCompensation newPending(String orderNo, String sceneRef,
                                                     InvoiceLinkKindEnum kind, String lastError) {
        return InvoiceLinkCompensation.builder()
                .orderNo(orderNo)
                .sceneRef(truncate(sceneRef, SCENE_REF_MAX_LENGTH))
                .kind(kind == null ? null : kind.getCode())
                .status(InvoiceLinkCompensationStatusEnum.PENDING.getCode())
                .retryCount(0)
                // 「调度器就绪后即刻可拾取」：首发不延迟，退避只发生在重试失败之后
                .nextRetryAt(LocalDateTime.now())
                .lastError(truncate(lastError, LAST_ERROR_MAX_LENGTH))
                .build();
    }

    /** 超长截断（{@code null} 原样返回）。 */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
