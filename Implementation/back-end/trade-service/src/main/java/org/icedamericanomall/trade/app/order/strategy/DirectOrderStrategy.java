package org.icedamericanomall.trade.app.order.strategy;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.dto.AddressCO;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.icedamericanomall.trade.domain.enums.StockTypeEnum;
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
    private final FreightPostageResolver freightPostageResolver;

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
        if (StockTypeEnum.requiresStockCheck(sku.getStockType())) {
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
        snap.setFreightTemplateId(sku.getFreightTemplateId()); // V5.6: 运费模板
        ctx.setSnapshots(List.of(snap));
        ctx.setSellerId(sku.getSellerId());

        AddressCO address = addressClient.getAddress(ctx.getAddressId());
        if (address == null) throw new BizException(ErrorCode.PARAM_ERROR, "收货地址不存在");
        ctx.setAddress(address);
    }

    @Override
    public Order buildOrder(OrderCreateContext ctx) {
        Order order = NormalCartOrderStrategy.buildBaseOrder(ctx);
        order.setOrderType(OrderTypeEnum.DIRECT.getCode());
        int total = ctx.getTotalAmount();

        // V4.3: 多券叠加（与 NormalCartOrderStrategy 共用 applyCoupons）
        int discount = NormalCartOrderStrategy.applyCoupons(couponClient, ctx, order, total);
        // V5.6（F2 运费模型）：运费单列快照；金额不变式 payAmount = total − discount + postFee
        int postFee = freightPostageResolver.resolve(ctx.getSellerId(), ctx.getSnapshots(), total);

        order.setDiscountAmount(discount);
        order.setPostFee(postFee);
        order.setPayAmount(total - discount + postFee);
        ctx.setDiscountAmount(discount);
        NormalCartOrderStrategy.fillAddress(ctx, order);
        return order;
    }

    @Override
    public void afterCreate(OrderCreateContext ctx, Order order) {
        // V4.1: 立即购买不操作购物车（京东标准）
    }
}
