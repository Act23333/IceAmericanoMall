package org.icedamericanomall.trade.domain.model.order;

/**
 * 订单项所属商品的「实时状态」读模型值对象（领域层，ADR-061 §决策 3 / ADR-057）。
 *
 * <p><b>为何在领域层而不是应用层端口里</b>：与 {@link SkuCurrentState} 同因 ——
 * 比对规则（{@link org.icedamericanomall.trade.domain.service.OrderItemPriceComparator}）
 * 是领域职责，其入参类型必须由领域层拥有，否则领域服务反向依赖应用层端口
 * （architecture-tests {@code domainDependsOnNothingOutward} 机械守卫，ADR-038）。
 *
 * <p>纯数据载体（record，不可变），<b>无框架依赖</b>；仅用于展示口径，不作业务判定。
 *
 * @param spuId    SPU 业务键（32-hex）
 * @param status   SPU 状态（1=上架 / 2=下架 / 3=删除）
 * @param minPrice 在售 SKU 最低价（分；无在售 SKU 时为 {@code null}）—— 用于「同款现价」提示
 */
public record SpuCurrentState(String spuId, Integer status, Integer minPrice) {
}
