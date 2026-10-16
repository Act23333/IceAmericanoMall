package org.icedamericanomall.trade.domain.model.order;

/**
 * 订单项「实时状态」读模型值对象（领域层，ADR-061 §决策 3 / ADR-057）。
 *
 * <p><b>为何在领域层而不是应用层端口里</b>：比对规则（{@link
 * org.icedamericanomall.trade.domain.service.OrderItemPriceComparator}）属领域职责，
 * 其入参类型必须由领域层拥有 —— 否则领域服务会反向依赖应用层端口，违反四层单向依赖
 * （architecture-tests {@code domainDependsOnNothingOutward} 已机械守卫，ADR-038）。
 * 应用层端口与基础设施实现只<b>引用</b>本值对象，方向保持 app/infra → domain。
 *
 * <p>本类型是纯数据载体（record，不可变），<b>无任何框架依赖</b>，不代表订单域事实：
 * 它由只读读模型填充，输出<b>仅用于展示</b>，不得用于下单/退款/售后等业务判定。
 *
 * @param skuId     SKU 业务键（32-hex；与查询入参同型同义，便于调用方自校验）
 * @param spuId     SKU 现属 SPU 业务键（32-hex）
 * @param price     现价（分；{@code sku.price}）
 * @param stock     现库存（{@code sku.stock}）
 * @param stockType 库存类型（1=LIMITED / 2=UNLIMITED / 3=PRESALE）—— <b>缺货判定必须结合它</b>：
 *                  {@code UNLIMITED} 的 SKU 库存恒为 0 也<b>不是</b>缺货（见
 *                  {@link org.icedamericanomall.trade.domain.service.StockAvailabilityPolicy}）
 * @param status    SKU 状态（0=停售 / 1=在售；见 {@code sku.status}）
 */
public record SkuCurrentState(String skuId, String spuId, Integer price, Integer stock,
                              Integer stockType, Integer status) {
}
