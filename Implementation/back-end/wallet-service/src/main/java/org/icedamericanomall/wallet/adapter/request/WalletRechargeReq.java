package org.icedamericanomall.wallet.adapter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 充值请求（{@code POST /api/wallet/recharge}，适配层入参）。
 *
 * <p><b>为什么是查询参数对象而不是 {@code @RequestBody}</b>：本端点的历史契约为
 * {@code POST /api/wallet/recharge?amount=100}（查询/表单参数，非 JSON body）。
 * 本类让 Spring 以属性绑定承接同一组参数（{@code amount} 参数名与语义不变），
 * 从而在「入参对象化」的同时保持 HTTP 契约零改动——不切到 JSON body，不改参数名。
 *
 * <p>{@code @Validated} 校验失败由 ia-common 全局异常处理器映射为
 * {@code PARAM_ERROR}(1101/HTTP 400)（此前仅由方法级 {@code @Validated} 产生
 * {@code ConstraintViolationException}，现已补齐映射）。
 */
@Data
public class WalletRechargeReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 充值金额（单位：分，必须 ≥ 1） */
    @NotNull(message = "充值金额不能为空")
    @Min(value = 1, message = "充值金额必须大于0")
    private Integer amount;
}
