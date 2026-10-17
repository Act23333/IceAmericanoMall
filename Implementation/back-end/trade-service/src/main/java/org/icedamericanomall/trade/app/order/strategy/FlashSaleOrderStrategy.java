package org.icedamericanomall.trade.app.order.strategy;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V4.1: 秒杀订单策略（京东秒杀漏斗模型 — 第四层：订单落库）。
 * 前置条件: marketing-service 已完成 Redis Lua 预扣，flashPrice + flashId 已设置到 ctx。
 * 校验: SKU存在(查item-service取商品名/规格/图片) → 地址快照
 * 后置: 不操作购物车
 */
@Component
@RequiredArgsConstructor
public class FlashSaleOrderStrategy implements OrderCreateStrategy {

    private final SkuClient skuClient;
    private final AddressClient addressClient;

    @Override
    public OrderTypeEnum supportedType() {
        return OrderTypeEnum.FLASH_SALE;
    }

    @Override
    public void validate(OrderCreateContext ctx) {
        // 从 item-service 获取 SKU 详情（商品名、规格、图片等快照数据）
        List<SkuCO> skuList = skuClient.getSkuListByIds(List.of(ctx.getSkuId()));
        if (skuList == null || skuList.isEmpty())
            // 资源不存在 = 真 404（批次 ①）：改造前借用 3000（200 信封）
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "SKU不存在");

        SkuCO sku = skuList.get(0);
        // 秒杀不限库存（Redis 已预扣），不校验 stock

        // 构建秒杀价快照
        OrderCreateContext.CartItemSnapshot snap = new OrderCreateContext.CartItemSnapshot();
        snap.setSkuId(sku.getSkuId());        // ADR-061：订单项 SKU 业务键快照
        snap.setSpuId(sku.getSpuId());        // ADR-061：订单项 SPU 业务键快照
        snap.setSellerId(sku.getSellerId());
        snap.setSpuName(sku.getSpuName());
        snap.setSkuSpec(sku.getSpec());
        snap.setPrice(ctx.getFlashPrice());  // ← 秒杀价，非 SKU 原价
        snap.setQuantity(ctx.getQuantity());
        snap.setImage(sku.getImage());
        ctx.setSnapshots(List.of(snap));
        ctx.setSellerId(sku.getSellerId());

        // 地址快照 + 越权防护唯一出口：归属校验在 bindAddress 内（内部端点不校验归属）
        ctx.bindAddress(addressClient.getAddress(ctx.getAddressId()));
    }

    @Override
    public Order buildOrder(OrderCreateContext ctx) {
        Order order = NormalCartOrderStrategy.buildBaseOrder(ctx);
        order.setOrderType(OrderTypeEnum.FLASH_SALE.getCode());
        // 秒杀不使用优惠券（秒杀价已是底价）：订单项快照价即秒杀价，
        // 故 原价 totalAmount = Σ(item.subTotal)，应付 payAmount 同为秒杀总额。
        // 注意：切勿把 payAmount 写入 totalAmount —— 二者语义不同（原价 vs 应付），
        // 折扣单（NORMAL/DIRECT 用券）下二者不相等。
        //
        // V5.6（F2 运费模型）第 6 条：秒杀订单（orderType=3，无店铺归属）postFee = 0。
        order.setTotalAmount(ctx.getTotalAmount());
        order.setDiscountAmount(0);
        order.setPostFee(0);
        order.setPayAmount(ctx.getTotalAmount());
        NormalCartOrderStrategy.fillAddress(ctx, order);
        return order;
    }

    @Override
    public void afterCreate(OrderCreateContext ctx, Order order) {
        // 秒杀不操作购物车
    }
}
