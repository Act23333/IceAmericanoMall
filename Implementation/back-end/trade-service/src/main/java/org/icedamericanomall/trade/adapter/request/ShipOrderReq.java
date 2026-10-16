package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 商家发货请求（{@code POST /api/trade/seller/order/{orderNo}/ship}）。
 *
 * <p>物流单号与物流公司是发货事实的必填要素（发货后需落 {@code order_logistics} 并对外可查），
 * 故在 Adapter 层以 Bean Validation 前置校验，缺失即 {@code PARAM_ERROR}。
 *
 * <p><b>V5.6 拆单发货（F3）</b>：新增 {@code skuIds} —— 本包裹的发货明细：
 * <ul>
 *   <li>空 / 缺省 = <b>全部剩余未发货明细</b>（同一包裹，兼容存量单包裹语义）；</li>
 *   <li>非空 = 仅本次发货的明细（部分发货时订单保持"待发货"，全部明细发货后才转"待收货"）。</li>
 * </ul>
 */
@Data
public class ShipOrderReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 物流单号（必填）。 */
    @NotBlank(message = "物流单号不能为空")
    private String logisticsNumber;

    /** 物流公司（必填）。 */
    @NotBlank(message = "物流公司不能为空")
    private String logisticsCompany;

    /**
     * 本包裹发货的明细 SKU 集合（V5.6，F3）；空/缺省 = 全部剩余未发货明细。
     *
     * <p><b>口径（契约 §1.8② + ADR-061 §决策 2）</b>：名含 {@code sku_id} 的入参一律按
     * <b>SKU 业务键</b> 32-hex 消费 —— 与 {@code OrderItemVO.sku_id} 同型同义，
     * 商家端从订单明细直接勾选即可（无需技术键）。订单项自 ADR-061 起即以业务键存储，
     * 故本字段与 {@code order_item.sku_id} 直接可比。
     */
    private List<String> skuIds;
}
