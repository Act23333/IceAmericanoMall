package org.icedamericanomall.trade.app.order.strategy;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.trade.app.order.pricing.PricingService;
import org.icedamericanomall.trade.domain.model.order.Order;
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
 * 计价: 委托价格计算中心 {@link PricingService}（F5，ADR-043：促销+会员+券统一计价）
 * 后置: 不操作购物车
 */
@Component
@RequiredArgsConstructor
public class DirectOrderStrategy implements OrderCreateStrategy {

    private final SkuClient skuClient;
    private final AddressClient addressClient;
    private final PricingService pricingService;
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
        snap.setSpuId(sku.getSpuId());        // V4.3: scope 校验
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

        // 越权防护唯一出口：地址归属校验在 bindAddress 内（addressId 由前端传入，内部端点不校验归属）
        ctx.bindAddress(addressClient.getAddress(ctx.getAddressId()));
    }

    @Override
    public Order buildOrder(OrderCreateContext ctx) {
        Order order = NormalCartOrderStrategy.buildBaseOrder(ctx);
        order.setOrderType(OrderTypeEnum.DIRECT.getCode());
        int total = ctx.getTotalAmount();

        // F5 价格计算中心（ADR-043）：促销(I44)+会员(I45)+券 统一计价，收编原 applyCoupons
        // ⚠ 券核销（远程副作用，marketing 侧独立事务）发生在本次调用内部：其成功事实由
        //   PricingService 置入 ctx.couponConsumed（与金额无关）。因此本行之后的任何一步失败
        //   （如 I43 运费 fail-closed）都必须由编排层回收券 —— 绝不能依赖 ctx.discountAmount 判定。
        int discount = pricingService.calculate(ctx, order);
        // 派生金额一旦算出立即回填（补偿判定不依赖它，见 couponConsumed 事实标记）
        ctx.setDiscountAmount(discount);
        // V5.6（F2 运费模型）：运费单列快照；金额不变式 payAmount = total − discount + postFee
        int postFee = freightPostageResolver.resolve(ctx.getSellerId(), ctx.getSnapshots(), total);

        order.setDiscountAmount(discount);
        order.setPostFee(postFee);
        order.setPayAmount(total - discount + postFee);
        NormalCartOrderStrategy.fillAddress(ctx, order);
        return order;
    }

    @Override
    public void afterCreate(OrderCreateContext ctx, Order order) {
        // V4.1: 立即购买不操作购物车（京东标准）
    }
}
