package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.icedamericanomall.common.constant.CsvIdLimits;

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
     *
     * <p><b>条数上限（批次 B7b，ADR-063 §决策 7 表 #6）</b>：{@code @Size(max = MAX_ORDER_ITEM_IDS)}
     * 即 {@code MAX_CART_ITEMS}(200) —— 判据是<b>请求体预算</b>（200 项 × 32-hex ≈ 6.4 KB），
     * <b>不得</b>借用列宽（15）或 URL（4）的判据。订单明细的来源是购物车/直购，
     * 明细数超过购物车条目上限即意味着绕过了加购闸门，属非法请求（400 PARAM_ERROR）。
     */
    @Size(max = CsvIdLimits.MAX_ORDER_ITEM_IDS,
            message = "发货明细不能超过" + CsvIdLimits.MAX_ORDER_ITEM_IDS + "项")
    private List<String> skuIds;
}
