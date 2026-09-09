package org.icedamericanomall.strategy;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.CouponClient;
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
        List<SkuDTO> skuList = skuClient.getSkuListByIds(List.of(ctx.getSkuId()));
        if (skuList == null || skuList.isEmpty())
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "SKU不存在");

        SkuDTO sku = skuList.get(0);
        if (sku.getStockType() == null || sku.getStockType() == 1) {
            if (sku.getStock() < ctx.getQuantity())
                throw new BizException(ErrorCode.STOCK_INSUFFICIENT);
        }

        OrderCreateContext.CartItemSnapshot snap = new OrderCreateContext.CartItemSnapshot();
        snap.setSkuId(ctx.getSkuId());
        snap.setSellerId(sku.getSellerId());
        snap.setProductName(sku.getProductName());
        snap.setSkuSpec(sku.getSpec());
        snap.setPrice(sku.getPrice());
        snap.setQuantity(ctx.getQuantity());
        snap.setImage(sku.getImage());
        ctx.setSnapshots(List.of(snap));
        ctx.setSellerId(sku.getSellerId());

        AddressDTO address = addressClient.getAddress(ctx.getAddressId());
        if (address == null) throw new BizException(ErrorCode.PARAM_ERROR, "收货地址不存在");
        ctx.setAddress(address);
    }

    @Override
    public OrderEntity buildOrder(OrderCreateContext ctx) {
        OrderEntity order = NormalCartOrderStrategy.buildBaseOrder(ctx);
        order.setOrderType(OrderTypeEnum.DIRECT.getCode());
        int total = ctx.getTotalAmount();
        int discount = 0;
        if (ctx.getUserCouponId() != null) {
            Integer applied = couponClient.useCoupon(
                    ctx.getUserId(), ctx.getUserCouponId(), order.getOrderNo(), total,
                    ctx.getOrderType().getCode(), ctx.getSellerId());
            discount = applied != null ? Math.min(applied, total) : 0;
        }
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
