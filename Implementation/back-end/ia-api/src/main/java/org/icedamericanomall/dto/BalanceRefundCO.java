package org.icedamericanomall.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 余额退款入账请求（跨服务契约对象）—— pay-service → wallet-service（I41 {@code POST /internal/balance/refund}）。
 *
 * <p><b>幂等</b>：{@code transactionNo} 是幂等键（等同于退款单号 {@code refund_no}）——
 * wallet-service 侧 {@code wallet_transaction.uk_transaction_no} 唯一约束保证
 * 「同一退款只入账一次」，重复请求幂等成功返回、不重复加余额。
 *
 * <p><b>字段命名（重要）</b>：wallet-service 的 Jackson 配置为
 * {@code spring.jackson.property-naming-strategy=SNAKE_CASE}，而调用方 pay-service 为默认
 * camelCase。为消除两侧策略差异造成「字段静默为 null」的风险，本对象的每个字段都用
 * {@link JsonProperty} <b>显式钉死线上字段名</b>（snake_case），与 wallet-service 侧接收对象
 * （{@code BalanceRefundReq}）保持一致 —— 线上字段名不再依赖任一服务的全局命名策略。
 */
@Data
public class BalanceRefundCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流水号（幂等键，UUID；= 退款单号）。 */
    @JsonProperty("transaction_no")
    private String transactionNo;

    /** 入账用户 ID。 */
    @JsonProperty("user_id")
    private Long userId;

    /** 入账金额（分，{@code > 0}）。 */
    @JsonProperty("amount")
    private Integer amount;

    /** 业务关联号（退款单号 {@code refund_no}）。 */
    @JsonProperty("biz_no")
    private String bizNo;
}
