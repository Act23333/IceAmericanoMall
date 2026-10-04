package org.icedamericanomall.pay.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对账批次视图对象（F4）—— AD44 列表与 AD46 触发返回。契约见 {@code 05-API-Specification.md} §4.13.14。
 */
@Data
public class ReconciliationBatchVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 批次技术主键（供 AD45 明细查询）。 */
    private Long id;

    /** 账单日（{@code yyyy-MM-dd}）。 */
    private LocalDate billDate;

    /** 对账渠道：WECHAT / ALIPAY。 */
    private String channel;

    /** 状态：0-处理中 / 1-完成 / 2-失败。 */
    private Integer status;

    /** 渠道账单总额（分；失败批次为 {@code null}）。 */
    private Integer channelTotal;

    /** 本地支付成功总额（分；失败批次为 {@code null}）。 */
    private Integer localTotal;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
