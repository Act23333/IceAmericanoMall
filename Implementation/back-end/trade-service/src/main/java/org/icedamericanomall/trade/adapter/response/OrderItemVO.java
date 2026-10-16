package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单项视图对象 —— 下单时点的「标识 + 快照」（ADR-061 §决策 1）。
 *
 * <p><b>两部分不得互相顶替</b>：
 * <ul>
 *   <li><b>标识（业务键，可回链实时表）</b>：{@code id}（明细行主键，仅作渲染 key）/
 *       {@code skuId}（SKU 业务键 32-hex）/ {@code spuId}（SPU 业务键 32-hex）；</li>
 *   <li><b>快照（下单冻结，绝不回查实时表）</b>：{@code spuName}（原 {@code productName}，
 *       ADR-061 §决策 5）/ {@code skuSpec} / {@code image} / {@code price} /
 *       {@code quantity} / {@code subTotal}。</li>
 * </ul>
 *
 * <p><b>标识口径（契约 §1.8 / §4.6.1）</b>：{@code sku_id}/{@code spu_id} 均为业务键
 * 32-hex {@code String}（原 {@code skuId: Long} 技术键已随 ADR-061 收口）——
 * 自增技术键在分库分表后不再全局唯一，不能作为跨分片/跨服务引用。
 * {@code id} 保持技术键（§1.8 例外：资源行主键，渲染/定位用）。
 *
 * <p><b>比价态不在此处</b>：契约 §十二 12.2 明确「比价结果字段形态未定、前端不得对接」，
 * 故本 VO <b>不</b>新增 {@code current_price}/{@code compare_state} 等键；
 * 服务端读模型（{@code OrderCurrentStateEnricher}）已具备产出能力，待契约定稿后再出栈。
 *
 * <p>{@code orderId}（技术外键）与审核字段未暴露。
 */
@Data
public class OrderItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单项 ID（渲染 key；资源行主键）。 */
    private Long id;

    /** SKU 业务键（{@code sku.sku_id} 的 32-hex 形态）。 */
    private String skuId;

    /** SPU 业务键（{@code spu.spu_id} 的 32-hex 形态）—— 商品源头回链/比价的锚点。 */
    private String spuId;

    /** SPU 名快照（原 {@code productName}）。 */
    private String spuName;

    /** 规格快照。 */
    private String skuSpec;

    /** 成交单价快照（分）。 */
    private Integer price;

    private Integer quantity;

    /** 小计（分）= price * quantity。 */
    private Integer subTotal;

    /** 图片快照。 */
    private String image;

    /**
     * 归属包裹号（V5.6 拆单发货增量）—— 对应物流记录 {@code packageNo}（{@code /api/logistics/{orderId}}）；
     * {@code null} = 尚未发货。字段上线前报文不含该键，前端须按可空字段消费。
     */
    private Integer packageNo;
}
