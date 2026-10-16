package org.icedamericanomall.trade.domain.model.order;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单项聚合（纯 POJO）—— {@code order_item} 表，下单时点的「标识 + 快照」。
 *
 * <p><b>标识（ADR-061 §决策 1，业务键，可回链实时表）</b>：{@link #spuId} / {@link #skuId}
 * 均为 32-hex <b>业务键</b>（{@code spu.spu_id} / {@code sku.sku_id}，UUIDv7 + {@code BINARY(16)}）。
 * 领域层即使用业务键 —— 订单项对本项目不存在「按技术键 join」的需求，且业务键让
 * 「历史订单回溯到商品源头」在 SKU 行已删除时仍能落到 SPU 粒度。
 *
 * <p><b>快照（下单冻结，绝不回查实时表）</b>：{@link #spuName} / {@link #skuSpec} /
 * {@link #price} / {@link #quantity} / {@link #subTotal} / {@link #image}。
 * 「现在是否下架/改价」按定义是<b>读时</b>信息，由读模型（ADR-057/061）旁路产出，
 * <b>不得</b>写回本聚合。
 */
@Data
public class OrderItem {

    private Long id;

    /** 所属订单技术主键（订单内部关联，不出线格式）。 */
    private Long orderId;

    /** SKU 业务键（32-hex；{@code sku.sku_id}）。 */
    private String skuId;

    /** SPU 业务键（32-hex；{@code spu.spu_id}）—— 回链商品页/比价的锚点。 */
    private String spuId;

    /** SPU 名快照（原 {@code productName}，ADR-061 §决策 5）。 */
    private String spuName;

    /** 规格快照。 */
    private String skuSpec;

    /** 单价快照（分）。 */
    private Integer price;

    private Integer quantity;

    /** 小计（分）。 */
    private Integer subTotal;

    /** 图片快照。 */
    private String image;

    /**
     * 归属包裹号（V5.6 拆单发货，F3）—— 发货时标记，对应 {@code order_logistics.package_no}；
     * {@code null} = 未发货；一明细只发一次（重复发货同一 sku 被拒）。
     */
    private Integer packageNo;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
