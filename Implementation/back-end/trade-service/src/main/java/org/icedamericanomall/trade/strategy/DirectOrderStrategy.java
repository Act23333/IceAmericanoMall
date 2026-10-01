package org.icedamericanomall.trade.strategy;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.trade.domain.entity.OrderEntity;
import org.icedamericanomall.dto.AddressCO;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.trade.enums.OrderTypeEnum;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V4.1: 立即购买策略（京东标准：商品详情页直接下单）。
 * 校验: 单SKU存在 → 限量库存检查 → 地址快照
 * 后置: 不操作购物车
 */
@Component
@RequiredArgsConstructor
public class DirectOrderStrategy implements OrderCreateStrategy {

    private final SkuClient skuClient;
    private final AddressClient addressClient;
    private final CouponClient couponClient;

    @Override
    public OrderTypeEnum supportedType() {
        return OrderTypeEnum.DIRECT;
    }

    @Override
    public void validate(OrderCreateContext ctx) {
        List<SkuCO> skuList = skuClient.getSkuListByIds(List.of(ctx.getSkuId()));
        if (skuList == null || skuList.isEmpty())
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "SKU不存在");

        SkuCO sku = skuList.get(0);
        if (sku.getStockType() == null || sku.getStockType() == 1) {
            if (sku.getStock() < ctx.getQuantity())
                throw new BizException(ErrorCode.STOCK_INSUFFICIENT);
        }

        OrderCreateContext.CartItemSnapshot snap = new OrderCreateContext.CartItemSnapshot();
        snap.setSkuId(ctx.getSkuId());
        snap.setProductId(sku.getProductId());        // V4.3: scope 校验
        snap.setCategoryId(sku.getCategoryId());      // V4.3: scope 校验
        snap.setSellerId(sku.getSellerId());
        snap.setProductName(sku.getProductName());
        snap.setSkuSpec(sku.getSpec());
        snap.setPrice(sku.getPrice());
        snap.setQuantity(ctx.getQuantity());
        snap.setImage(sku.getImage());
        ctx.setSnapshots(List.of(snap));
        ctx.setSellerId(sku.getSellerId());

        AddressCO address = addressClient.getAddress(ctx.getAddressId());
        if (address == null) throw new BizException(ErrorCode.PARAM_ERROR, "收货地址不存在");
        ctx.setAddress(address);
    }

    @Override
    public OrderEntity buildOrder(OrderCreateContext ctx) {
        OrderEntity order = NormalCartOrderStrategy.buildBaseOrder(ctx);
        order.setOrderType(OrderTypeEnum.DIRECT.getCode());
        int total = ctx.getTotalAmount();

        // V4.3: 多券叠加（与 NormalCartOrderStrategy 共用 applyCoupons）
        int discount = NormalCartOrderStrategy.applyCoupons(couponClient, ctx, order, total);

        order.setDiscountAmount(discount);
        order.setPayAmount(total - discount);
        ctx.setDiscountAmount(discount);
        NormalCartOrderStrategy.fillAddress(ctx, order);
        return order;
    }

    @Override
    public void afterCreate(OrderCreateContext ctx, OrderEntity order) {
        // V4.1: 立即购买不操作购物车（京东标准）
    }
}
