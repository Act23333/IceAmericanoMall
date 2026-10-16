package org.icedamericanomall.trade.domain.model.order;

import java.util.List;

/**
 * 拆单发货结果（F3 拆单发货，V5.6）—— 一次「发货一个包裹」的事实载体。
 *
 * <p>由 {@code OrderRepository#shipPackage} 在<b>本地事务内</b>构造并返回；编排层
 * （{@code OrderLifecycleManager}）据此在<b>事务外</b>创建物流记录与发布领域事件
 * （Feign / MQ 严禁进事务）。
 *
 * @param orderId        订单 ID（物流记录定位用）
 * @param packageNo      本次包裹号（= 本单已发包裹数 + 1）
 * @param shippedSkuIds  本次包裹覆盖的明细 skuId 集合
 * @param allShipped     发完本包裹后是否「全部明细已发货」（true = 订单已迁移为待收货）
 */
public record ShipPackageResult(Long orderId, int packageNo, List<String> shippedSkuIds, boolean allShipped) {
}
