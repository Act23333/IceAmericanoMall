package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.io.Serializable;

/**
 * 售后申请请求（{@code POST /api/after-sale}）。
 *
 * <p>userId 由登录态（{@code UserContext}）注入，请求体不承载用户身份。
 * 这里的约束与领域服务 {@code AfterSaleServiceImpl.requireAfterSaleableOrder} 的前置校验同源
 * （订单号非空、类型合法、退款金额 &gt; 0），使非法入参在 Adapter 层即被拒绝为 {@code PARAM_ERROR}；
 * <b>退款金额上限（≤ 订单实付）依赖订单数据，仍由领域服务校验</b>。
 *
 * <p><b>type 校验收紧（V5.4）</b>：此前仅 {@code @NotNull}，非法值（如 9）会一路落库为脏数据
 * （下游按 type 分支渲染/退货流程时不可解释）。现按 {@link org.icedamericanomall.trade.domain.enums.AfterSaleTypeEnum}
 * 的合法码区间（{@code MIN_CODE..MAX_CODE}）做区间校验，与领域服务内的枚举解析双重把关。
 */
@Data
public class AfterSaleApplyReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号（必填，且订单须归属当前用户、处于待收货/已完成状态）。 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 售后类型：1-退货退款，2-仅退款（取值见 {@code AfterSaleTypeEnum}）。 */
    @NotNull(message = "售后类型不能为空")
    @Range(min = org.icedamericanomall.trade.domain.enums.AfterSaleTypeEnum.MIN_CODE,
            max = org.icedamericanomall.trade.domain.enums.AfterSaleTypeEnum.MAX_CODE,
            message = "售后类型非法：1-退货退款，2-仅退款")
    private Integer type;

    /** 申请原因。 */
    private String reason;

    /** 退款金额（分）：&gt; 0 且不超过订单实付金额。 */
    @Min(value = 1, message = "退款金额必须大于 0")
    private Integer refundAmount;

    /** 退货物流单号（退货退款场景填写）。 */
    private String logisticsNumber;
}
