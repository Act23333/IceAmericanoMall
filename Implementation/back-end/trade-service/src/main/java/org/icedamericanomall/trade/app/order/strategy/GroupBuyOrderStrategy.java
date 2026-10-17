package org.icedamericanomall.trade.app.order.strategy;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.GroupBuyClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.trade.app.order.GroupBuyJoinContextStore;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.dto.GroupBuyValidateCO;
import org.icedamericanomall.dto.GroupBuyValidateResultCO;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.icedamericanomall.trade.domain.enums.StockTypeEnum;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * F13 拼团下单策略（T27, ADR-049 两段式参团第一步）。
 *
 * <p><b>流程</b>：I52 validate（marketing fail-closed：活动进行中 + 参团时团有效未满 + 用户未在该团）
 * 下发拼团价与活动绑定 SKU（服务端权威绑定，不信任客户端）→ SKU 快照（item-service）→
 * buildOrder（subTotal = 拼团价 × 1，quantity 恒 1；discountAmount = 0 不进 PricingService，
 * 与秒杀同口径 ADR-043；postFee 走 {@link FreightPostageResolver} 常规计价）→ 共享管道
 * deductStock/persist → afterCreate 把参团上下文（activityId/teamNo）写入 Redis 供支付成功参团消费。
 *
 * <p><b>两段式语义</b>：下单<b>不占团位</b>——未支付订单走既有 30 分钟超时取消，零团残留；
 * 支付成功后才经 I53 join 入团（见
 * {@link org.icedamericanomall.trade.app.order.GroupBuyParticipationService}）。
 */
@Component
@RequiredArgsConstructor
public class GroupBuyOrderStrategy implements OrderCreateStrategy {

    /** 拼团购买数量恒为 1（一人一单一件，uk_order_no 天然限购，ADR-049 Phase 1 边界） */
    private static final int GROUP_BUY_QUANTITY = 1;

    private final GroupBuyClient groupBuyClient;
    private final SkuClient skuClient;
    private final AddressClient addressClient;
    private final FreightPostageResolver freightPostageResolver;
    private final GroupBuyJoinContextStore joinContextStore;

    @Override
    public OrderTypeEnum supportedType() {
        return OrderTypeEnum.GROUP_BUY;
    }

    @Override
    public void validate(OrderCreateContext ctx) {
        // 1. I52 下单前置校验（fail-closed）：拼团价/活动绑定 SKU 是计价业务事实，降级即下单失败
        GroupBuyValidateResultCO result = groupBuyClient.validate(
                new GroupBuyValidateCO(ctx.getActivityId(), ctx.getTeamNo(), ctx.getUserId()));
        if (result == null || result.getGroupPrice() == null || result.getSkuId() == null) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "拼团活动校验结果异常");
        }
        ctx.setGroupPrice(result.getGroupPrice());

        // 2. SKU 快照（skuId 由 I52 服务端下发；商品名/规格/图片等快照数据取自 item-service）
        SkuCO sku = loadSku(result.getSkuId());
        if (StockTypeEnum.requiresStockCheck(sku.getStockType()) && sku.getStock() < GROUP_BUY_QUANTITY) {
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT,
                    "商品 [" + sku.getSpuName() + "] 库存不足");
        }
        OrderCreateContext.CartItemSnapshot snap = new OrderCreateContext.CartItemSnapshot();
        snap.setSkuId(sku.getSkuId());        // ADR-061：订单项 SKU 业务键快照
        snap.setSpuId(sku.getSpuId());        // ADR-061：订单项 SPU 业务键快照
        snap.setCategoryId(sku.getCategoryId());
        snap.setSellerId(sku.getSellerId());
        snap.setSpuName(sku.getSpuName());
        snap.setSkuSpec(sku.getSpec());
        snap.setPrice(ctx.getGroupPrice());          // ← 拼团价快照，非 SKU 原价
        snap.setQuantity(GROUP_BUY_QUANTITY);        // 恒 1（一人一单一件）
        snap.setImage(sku.getImage());
        snap.setFreightTemplateId(sku.getFreightTemplateId()); // 运费常规计价依据
        ctx.setSnapshots(List.of(snap));
        ctx.setSellerId(sku.getSellerId());

        // 3. 地址快照 + 越权防护唯一出口：归属校验在 bindAddress 内（内部端点不校验归属）
        ctx.bindAddress(addressClient.getAddress(ctx.getAddressId()));
    }

    @Override
    public Order buildOrder(OrderCreateContext ctx) {
        Order order = NormalCartOrderStrategy.buildBaseOrder(ctx);
        order.setOrderType(OrderTypeEnum.GROUP_BUY.getCode());
        // 拼团价已是底价：不叠加券/促销/会员（discountAmount=0，与秒杀同口径，ADR-043）；
        // 运费单列快照走常规计价；金额不变式 payAmount = total − 0 + postFee
        int total = ctx.getTotalAmount();
        int postFee = freightPostageResolver.resolve(ctx.getSellerId(), ctx.getSnapshots(), total);
        order.setTotalAmount(total);
        order.setDiscountAmount(0);
        order.setPostFee(postFee);
        order.setPayAmount(total + postFee);
        NormalCartOrderStrategy.fillAddress(ctx, order);
        return order;
    }

    @Override
    public void afterCreate(OrderCreateContext ctx, Order order) {
        // 参团上下文一次性写入 Redis（key=groupbuy:order:ctx:{orderNo}，TTL 48h）；
        // 写失败异常上抛 = 下单即失败（Saga 补偿已扣库存），绝不让用户付款后陷入退款闭环
        joinContextStore.save(order.getOrderNo(), ctx.getActivityId(), ctx.getTeamNo());
    }

    /**
     * 按 I52 下发的活动绑定 SKU 查询快照数据（fail-closed：查不到即拒绝下单）。
     *
     * <p>{@code skuId} 为 SKU <b>业务键</b> 32-hex（ADR-061 §决策 2：I52 回执与 I13 入参同口径）。
     */
    private SkuCO loadSku(String skuId) {
        List<SkuCO> skuList = skuClient.getSkuListByIds(List.of(skuId));
        if (skuList == null || skuList.isEmpty()) {
            // 资源不存在 = 真 404（批次 ①）：改造前借用 3000（200 信封）
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "SKU不存在");
        }
        return skuList.get(0);
    }
}
