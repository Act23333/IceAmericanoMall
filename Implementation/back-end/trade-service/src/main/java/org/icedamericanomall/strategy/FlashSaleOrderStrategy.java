package org.icedamericanomall.strategy;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.dto.AddressDTO;
import org.icedamericanomall.dto.SkuDTO;
import org.icedamericanomall.enums.OrderTypeEnum;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
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
        List<SkuDTO> skuList = skuClient.getSkuListByIds(List.of(ctx.getSkuId()));
        if (skuList == null || skuList.isEmpty())
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "SKU不存在");

        SkuDTO sku = skuList.get(0);
        // 秒杀不限库存（Redis 已预扣），不校验 stock

        // 构建秒杀价快照
        OrderCreateContext.CartItemSnapshot snap = new OrderCreateContext.CartItemSnapshot();
        snap.setSkuId(ctx.getSkuId());
        snap.setSellerId(sku.getSellerId());
        snap.setProductName(sku.getProductName());
        snap.setSkuSpec(sku.getSpec());
        snap.setPrice(ctx.getFlashPrice());  // ← 秒杀价，非 SKU 原价
        snap.setQuantity(ctx.getQuantity());
        snap.setImage(sku.getImage());
        ctx.setSnapshots(List.of(snap));
        ctx.setSellerId(sku.getSellerId());

        // 地址快照
        AddressDTO address = addressClient.getAddress(ctx.getAddressId());
        if (address == null) throw new BizException(ErrorCode.PARAM_ERROR, "收货地址不存在");
        ctx.setAddress(address);
    }

    @Override
    public OrderEntity buildOrder(OrderCreateContext ctx) {
        OrderEntity order = NormalCartOrderStrategy.buildBaseOrder(ctx);
        order.setOrderType(OrderTypeEnum.FLASH_SALE.getCode());
        // 秒杀订单不使用优惠券（秒杀价已是底价）
        order.setTotalAmount(ctx.getTotalAmount());
        order.setDiscountAmount(0);
        order.setPayAmount(ctx.getTotalAmount());
        NormalCartOrderStrategy.fillAddress(ctx, order);
        return order;
    }

    @Override
    public void afterCreate(OrderCreateContext ctx, OrderEntity order) {
        // 秒杀不操作购物车
    }
}
