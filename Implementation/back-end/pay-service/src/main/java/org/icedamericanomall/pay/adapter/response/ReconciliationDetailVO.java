package org.icedamericanomall.pay.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对账差异明细视图对象（F4）—— AD45 返回。契约见 {@code 05-API-Specification.md} §4.13.14。
 */
@Data
public class ReconciliationDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细技术主键。 */
    private Long id;

    /** 所属批次 ID。 */
    private Long batchId;

    /** 本地支付单号（内部定位用；「渠道有本地无」的差异行没有本地支付单，故为空）。 */
    private String payOrderNo;

    /**
     * 对外渠道号（{@code pay_order.channel_trade_no}）—— <b>与渠道账单的比对键</b>。
     *
     * <p>本轮新增字段（仅新增，不改既有字段）：既有契约里明细的 {@code payOrderNo} 曾被误当作
     * 比对键，但渠道账单从不含内部支付单号；「渠道有本地无」的行更是<b>没有</b>本地支付单号，
     * 管理端无法据其追查。补齐本字段后，四态明细两侧都可定位。
     */
    private String channelTradeNo;

    /** 渠道交易号（渠道账单有则回填）。 */
    private String channelTxnId;

    /** 渠道账单金额（分；渠道无此单为 {@code null}）。 */
    private Integer channelAmount;

    /** 本地金额（分；本地无此单为 {@code null}）。 */
    private Integer localAmount;

    /** 比对结果：1-匹配 / 2-渠道有本地无 / 3-本地有渠道无 / 4-金额不一致。 */
    private Integer result;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
