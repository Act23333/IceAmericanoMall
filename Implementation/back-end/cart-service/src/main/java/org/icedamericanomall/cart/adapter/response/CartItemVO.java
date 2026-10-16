package org.icedamericanomall.cart.adapter.response;

import lombok.Data;

/**
 * 购物车项视图对象（对外所需字段）。
 *
 * <p>仅暴露前端结算/展示必需字段；审计技术字段（create_time/update_time）不外泄。
 *
 * <p><b>标识口径（ADR-058 §1 / ADR-061 §决策 2）</b>：{@code skuId} 为 <b>SKU 业务键</b>
 * 32-hex {@code String}，与 C4 {@code DELETE /api/cart/item/{skuId}}、C5
 * {@code PATCH /api/cart/item/{skuId}/selected} 的路径变量<b>同型同义</b> ——
 * 前端拿到本列表后可直接删/改自己的行（否则类型层不成立）。
 * {@code spuId} 同为 SPU 业务键（回链商品页用）。I13 回填失败时两者为 {@code null}
 * （fail-open，<b>不回落技术键</b>——ADR-061 §决策 3）。
 *
 * <p><b>名称字段（ADR-061 §决策 5）</b>：{@code spuName} ← {@code spu.name}
 * （原 {@code productName}）。{@code image} / {@code spec} 保持现状（前者是订单项图片快照同名字段，
 * 后者是 SKU 规格，均非 SPU 名/主图）。
 */
@Data
public class CartItemVO {
    private String skuId;
    private String spuId;
    private String spuName;
    private String spec;
    private String image;
    private Integer price;
    private Integer quantity;
    private Boolean selected;
    private Integer subTotal;  // 价格*数量，单位分
}
