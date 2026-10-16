package org.icedamericanomall.trade.app.order.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.CartClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.trade.app.order.pricing.PricingService;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.dto.AddressCO;
import org.icedamericanomall.dto.CartItemCO;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.icedamericanomall.trade.domain.enums.StockTypeEnum;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.IdGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * V4.1: 购物车下单策略。
 * 校验: 购物车非空 → SKU批量查询 → 限量库存检查 → 单店铺校验 → 地址快照
 * 计价: 委托价格计算中心 {@link PricingService}（F5，ADR-043：促销+会员+券统一计价）
 * 后置: 删除已购购物车项
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NormalCartOrderStrategy implements OrderCreateStrategy {

    private final CartClient cartClient;
    private final SkuClient skuClient;
    private final AddressClient addressClient;
    private final PricingService pricingService;
    private final FreightPostageResolver freightPostageResolver;

    @Override
    public OrderTypeEnum supportedType() {
        return OrderTypeEnum.NORMAL;
    }

    @Override
    public void validate(OrderCreateContext ctx) {
        List<CartItemCO> cartItems = cartClient.getSelectedItems(ctx.getUserId());
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "购物车为空，无法下单");
        }
        ctx.setCartItems(cartItems);

        List<Long> skuIds = cartItems.stream().map(CartItemCO::getSkuId).collect(Collectors.toList());
        List<SkuCO> skuList = skuClient.getSkuListByIds(skuIds);
        if (skuList == null || skuList.size() != skuIds.size()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "商品信息异常，请刷新后重试");
        }
        Map<Long, SkuCO> skuMap = skuList.stream()
                .collect(Collectors.toMap(SkuCO::getId, s -> s, (a, b) -> a));

        List<OrderCreateContext.CartItemSnapshot> snapshots = new ArrayList<>();
        Long sellerId = null;
        for (CartItemCO cartItem : cartItems) {
            SkuCO sku = skuMap.get(cartItem.getSkuId());
            if (sku == null) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "SKU不存在: " + cartItem.getSkuId());
            if (StockTypeEnum.requiresStockCheck(sku.getStockType())) {
                if (sku.getStock() < cartItem.getQuantity()) {
                    throw new BizException(ErrorCode.STOCK_INSUFFICIENT,
                            "商品 [" + sku.getProductName() + "] 库存不足");
                }
            }
            if (sellerId == null) sellerId = sku.getSellerId();
            else if (!sellerId.equals(sku.getSellerId())) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "暂不支持跨店下单，请分别结算");
            }
            OrderCreateContext.CartItemSnapshot snap = new OrderCreateContext.CartItemSnapshot();
            snap.setSkuId(cartItem.getSkuId());
            snap.setSpuId(sku.getSpuId());        // V4.3: scope 校验
            snap.setCategoryId(sku.getCategoryId());      // V4.3: scope 校验
            snap.setSellerId(sku.getSellerId());
            snap.setProductName(sku.getProductName());
            snap.setSkuSpec(sku.getSpec());
            snap.setPrice(sku.getPrice());
            snap.setQuantity(cartItem.getQuantity());
            snap.setImage(sku.getImage());
            snap.setFreightTemplateId(sku.getFreightTemplateId()); // V5.6: 运费模板
            snapshots.add(snap);
        }
        ctx.setSnapshots(snapshots);
        ctx.setSellerId(sellerId);

        // 越权防护唯一出口：地址归属校验在 bindAddress 内（addressId 由前端传入，内部端点不校验归属）
        ctx.bindAddress(addressClient.getAddress(ctx.getAddressId()));
    }

    @Override
    public Order buildOrder(OrderCreateContext ctx) {
        Order order = buildBaseOrder(ctx);
        order.setOrderType(OrderTypeEnum.NORMAL.getCode());

        int total = ctx.getTotalAmount();
        // F5 价格计算中心（ADR-043）：促销(I44)+会员(I45)+券 统一计价，收编原 applyCoupons
        // ⚠ 券核销（远程副作用，marketing 侧独立事务）发生在本次调用内部：其成功事实由
        //   PricingService 置入 ctx.couponConsumed（与金额无关）。因此本行之后的任何一步失败
        //   （如 I43 运费 fail-closed）都必须由编排层回收券 —— 绝不能依赖 ctx.discountAmount 判定。
        int discount = pricingService.calculate(ctx, order);
        // 派生金额一旦算出立即回填：补偿判定不依赖它，但它是后续步骤/日志所需的事实，
        // 不应晚于「券核销之后仍会失败的步骤」才可用（原时序缺陷即由此暴露）。
        ctx.setDiscountAmount(discount);
        // V5.6（F2 运费模型）：运费单列快照；金额不变式 payAmount = total − discount + postFee
        int postFee = freightPostageResolver.resolve(ctx.getSellerId(), ctx.getSnapshots(), total);
        order.setTotalAmount(total);
        order.setDiscountAmount(discount);
        order.setPostFee(postFee);
        order.setPayAmount(total - discount + postFee);

        fillAddress(ctx, order);
        return order;
    }

    @Override
    public void afterCreate(OrderCreateContext ctx, Order order) {
        List<CartItemCO> cartItems = ctx.getCartItems();
        if (cartItems == null || cartItems.isEmpty()) {
            return;
        }
        try {
            List<Long> orderedIds = toOrderedCartItemIds(cartItems, order);
            if (!orderedIds.isEmpty()) cartClient.deleteByIds(ctx.getUserId(), orderedIds);
        } catch (Exception e) {
            log.error("删除已购购物车项失败（非致命）: userId={}, orderNo={}", ctx.getUserId(),
                    order.getOrderNo(), e);
        }
    }

    /**
     * 已购购物车项主键抽取（I14 回执）。
     *
     * <p><b>缺陷修复（勿改回 filter 静默丢弃）</b>：{@code cartItemId} 由 cart-service
     * {@code CartConverter.itemToCO} 映射（源字段名 {@code id} ≠ 目标名 {@code cartItemId}，
     * 曾因未显式映射而恒 null）。取不到 id 时此前用 {@code .filter(id -> id != null)} 静默吞掉，
     * 线上表现为「下单成功后已购项永不清除」且无任何日志线索；此处改为 <b>可观测</b>：WARN 记明
     * 缺口并跳过该项，仍不阻断下单主流程（清车是后置非关键步骤，失败也只是留下待清理项）。
     */
    private List<Long> toOrderedCartItemIds(List<CartItemCO> cartItems, Order order) {
        List<Long> orderedIds = new ArrayList<>(cartItems.size());
        for (CartItemCO cartItem : cartItems) {
            if (cartItem.getCartItemId() == null) {
                log.warn("已购购物车项缺少 cartItemId，跳过该项删除（cart-service 转换映射缺口，"
                                + "请检查 CartConverter.itemToCO）: userId={}, orderNo={}, skuId={}",
                        order.getUserId(), order.getOrderNo(), cartItem.getSkuId());
                continue;
            }
            orderedIds.add(cartItem.getCartItemId());
        }
        return orderedIds;
    }

    // === Shared helpers used by all strategies ===

    static Order buildBaseOrder(OrderCreateContext ctx) {
        Order order = new Order();
        // V5.19：订单号由编排层在任何远程副作用（券核销）之前确定并放入 ctx，策略原样采用 ——
        // 它既是券核销的幂等锚点，也是补偿/兜底对账在订单对象丢失时定位该单的依据。
        // 兜底分支只为保持本静态工厂可独立使用（无编排层上下文时）。
        if (ctx.getOrderNo() == null) {
            ctx.setOrderNo(IdGenerator.nextId());
        }
        order.setOrderNo(ctx.getOrderNo());
        order.setUserId(ctx.getUserId());
        order.setSellerId(ctx.getSellerId());
        order.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());
        order.setRemark(ctx.getRemark()); // V5.7: 买家留言随单快照（无留言渠道的订单类型为 null）
        // F16 多币种（V5.17）: 展示币种/汇率随单快照——仅快照成功后两者非空
        //（未传/CNY/降级 fail-open 时 ctx 已归空，两列 NULL），展示参考、不参与金额不变式 V2
        order.setDisplayCurrency(ctx.getDisplayCurrency());
        order.setDisplayRate(ctx.getDisplayRate());
        int totalAmount = 0;
        for (var snap : ctx.getSnapshots()) {
            totalAmount += snap.getPrice() * snap.getQuantity();
        }
        ctx.setTotalAmount(totalAmount);
        return order;
    }

    static void fillAddress(OrderCreateContext ctx, Order order) {
        AddressCO addr = ctx.getAddress();
        order.setReceiverName(addr.getReceiver());
        order.setReceiverPhone(addr.getPhone());
        order.setReceiverAddress(addr.getProvince() + addr.getCity() + addr.getDistrict()
                + addr.getStreet() + addr.getDetail());
    }

    /** Build item entities from snapshots (orderId set later by OrderServiceImpl). */
    public static List<OrderItem> buildItems(Order order, OrderCreateContext ctx) {
        List<OrderItem> items = new ArrayList<>();
        for (var snap : ctx.getSnapshots()) {
            OrderItem item = new OrderItem();
            item.setSkuId(snap.getSkuId());
            item.setProductName(snap.getProductName());
            item.setSkuSpec(snap.getSkuSpec());
            item.setPrice(snap.getPrice());
            item.setQuantity(snap.getQuantity());
            item.setSubTotal(snap.getPrice() * snap.getQuantity());
            item.setImage(snap.getImage());
            items.add(item);
        }
        return items;
    }
}
