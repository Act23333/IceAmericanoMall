package org.icedamericanomall.trade.infrastructure.persistence.order;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单项持久化对象（{@code order_item}）—— 下单时点的「标识 + 快照」。
 *
 * <p><b>标识列（ADR-061 §决策 1）</b>：{@code sku_id} / {@code spu_id} 均为
 * {@code BINARY(16)} <b>业务键</b>（UUIDv7，ADR-050），PO 侧以 {@code byte[]} 承载
 * （与 {@code SpuPO.spuId} / {@code SkuPO.skuId} 同一口径：避免 {@code String} 对
 * {@code BINARY(16)} 列的静默查空）。<b>不是</b>技术外键 —— 原
 * {@code sku_id BIGINT} + {@code fk_orderitem_sku} 已随本决策移除。
 *
 * <p><b>快照列</b>：{@code spuName}（原 {@code productName}，ADR-061 §决策 5）/
 * {@code skuSpec} / {@code price} / {@code quantity} / {@code subTotal} / {@code image}，
 * 下单时点冻结，读取路径<b>绝不</b>回查 {@code spu}/{@code sku} 活表刷新。
 *
 * <p>{@code orderId} 保留技术外键 {@code BIGINT}（订单内部关联，不出线格式）。
 */
@Data
@TableName("order_item")
public class OrderItemPO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属订单（技术外键 → {@code orders.id}；订单内部关联，不出线格式）。 */
    private Long orderId;

    /** SKU 业务键（{@code BINARY(16)} ↔ {@code byte[]}；hex 形态仅在领域层，ADR-050）。 */
    private byte[] skuId;

    /** SPU 业务键（{@code BINARY(16)} ↔ {@code byte[]}）—— 商品源头回链/比价的锚点。 */
    private byte[] spuId;

    /** SPU 名快照（原 {@code product_name}，ADR-061 §决策 5）。 */
    private String spuName;

    /** 规格快照 */
    private String skuSpec;

    /** 单价快照（分） */
    private Integer price;

    private Integer quantity;

    /** 小计（分） */
    private Integer subTotal;

    /** 图片快照 */
    private String image;

    /**
     * 归属包裹号（V5.6 拆单发货，F3）—— 发货时标记，对应 {@code order_logistics.package_no}；
     * {@code null} = 未发货；一明细只发一次（重复发货同一 sku 被拒，{@code ORDER_STATUS_INVALID(3010)}）。
     */
    private Integer packageNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
