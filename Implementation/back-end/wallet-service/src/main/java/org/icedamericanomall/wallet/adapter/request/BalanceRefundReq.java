package org.icedamericanomall.wallet.adapter.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 余额退款入账请求（{@code POST /internal/balance/refund}，适配层入参）。
 *
 * <p><b>字段命名（重要）</b>：wallet-service 的 Jackson 全局策略是
 * {@code SNAKE_CASE}，而调用方 pay-service 为默认 camelCase。为避免「按各自全局策略编解码
 * 导致字段静默为 null」的跨服务陷阱，本类每个字段都用 {@link JsonProperty} <b>显式钉死</b>
 * 线上字段名（snake_case），与 ia-api 的 {@code BalanceRefundCO} 一一对应。
 *
 * <p><b>为什么独立定义而不复用 ia-api 的 CO</b>：wallet-service 刻意不依赖 ia-api
 * （见 {@code WalletApplication} 的扫描说明），内部端点入参由本服务自持，契约一致性由
 * 「线字段名对齐 + 契约测试」保证。
 */
@Data
public class BalanceRefundReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流水号（幂等键，= 退款单号）。 */
    @JsonProperty("transaction_no")
    @NotBlank(message = "流水号不能为空")
    private String transactionNo;

    /** 入账用户 ID。 */
    @JsonProperty("user_id")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 入账金额（分，> 0）。 */
    @JsonProperty("amount")
    @NotNull(message = "退款金额不能为空")
    @Min(value = 1, message = "退款金额必须大于0")
    private Integer amount;

    /** 业务关联号（退款单号）。 */
    @JsonProperty("biz_no")
    private String bizNo;
}
