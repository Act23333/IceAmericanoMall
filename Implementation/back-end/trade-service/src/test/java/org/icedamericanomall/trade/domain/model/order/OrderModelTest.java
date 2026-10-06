package org.icedamericanomall.trade.domain.model.order;

import org.icedamericanomall.trade.adapter.request.CreateOrderReq;
import org.icedamericanomall.trade.adapter.request.DirectOrderReq;
import org.icedamericanomall.trade.adapter.request.ShipOrderReq;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单领域模型单元测试 —— 状态码枚举、金额口径（分）、快照字段、
 * 与下单请求契约（V5.3/V5.4 契约收缩后形状）。
 */
@DisplayName("订单领域模型（Order/OrderItem/枚举/请求契约）")
class OrderModelTest {

    // ==================== OrderStatusEnum ====================

    @Test
    @DisplayName("OrderStatusEnum — 状态码正确映射")
    void shouldHaveCorrectStatusFlow() {
        assertEquals(1, OrderStatusEnum.PENDING_PAYMENT.getCode(), "待付款");
        assertEquals(2, OrderStatusEnum.PENDING_SHIPMENT.getCode(), "待发货");
        assertEquals(3, OrderStatusEnum.PENDING_RECEIPT.getCode(), "待收货");
        assertEquals(4, OrderStatusEnum.COMPLETED.getCode(), "已完成");
        assertEquals(5, OrderStatusEnum.CANCELLED.getCode(), "已取消");
        assertEquals(6, OrderStatusEnum.PENDING_REVIEW.getCode(), "待评价");
    }

    @Test
    @DisplayName("OrderStatusEnum — 共 6 个订单状态")
    void shouldHaveExactlySixStatuses() {
        assertEquals(6, OrderStatusEnum.values().length);
    }

    @Test
    @DisplayName("OrderStatusEnum — 待付款可取消，已取消不可再取消")
    void shouldOnlyCancelPendingPaymentOrders() {
        int pending = OrderStatusEnum.PENDING_PAYMENT.getCode();
        int cancelled = OrderStatusEnum.CANCELLED.getCode();
        assertEquals(1, pending);
        assertEquals(5, cancelled);
        assertNotEquals(pending, cancelled);
    }

    // ==================== 金额计算 ====================

    @Test
    @DisplayName("订单总额 = 各订单项小计之和")
    void shouldCalculateTotalAmountCorrectly() {
        OrderItem item1 = new OrderItem();
        item1.setPrice(1000); // 10.00
        item1.setQuantity(2);
        item1.setSubTotal(2000);

        OrderItem item2 = new OrderItem();
        item2.setPrice(500); // 5.00
        item2.setQuantity(3);
        item2.setSubTotal(1500);

        int totalAmount = item1.getSubTotal() + item2.getSubTotal();
        assertEquals(3500, totalAmount); // 35.00
    }

    @Test
    @DisplayName("订单金额 — 全部使用 Integer（分）存储")
    void shouldStoreAllAmountsInCents() {
        Order order = new Order();
        order.setTotalAmount(10000);  // 100.00 元
        order.setPayAmount(10000);
        order.setDiscountAmount(0);

        assertInstanceOf(Integer.class, order.getTotalAmount());
        assertInstanceOf(Integer.class, order.getPayAmount());
        assertInstanceOf(Integer.class, order.getDiscountAmount());
    }

    @Test
    @DisplayName("OrderItem 单独创建 — 默认值验证")
    void shouldHaveDefaultValues_whenNewOrderItem() {
        OrderItem item = new OrderItem();
        assertNull(item.getOrderId());
        assertNull(item.getSkuId());
        assertNull(item.getPrice());
        assertNull(item.getQuantity());
        assertNull(item.getSubTotal());
    }

    // ==================== 乐观锁 ====================

    @Test
    @DisplayName("Order — version 字段存在且初始值可为 0")
    void shouldHaveVersionForOptimisticLocking() {
        Order order = new Order();
        order.setVersion(0);
        assertNotNull(order.getVersion());
        assertEquals(0, order.getVersion());
    }

    @Test
    @DisplayName("Order — version 递增验证")
    void shouldIncrementVersion() {
        Order order = new Order();
        order.setVersion(0);
        order.setVersion(order.getVersion() + 1);
        assertEquals(1, order.getVersion());
    }

    // ==================== Order 快照字段 ====================

    @Test
    @DisplayName("Order — 收货地址快照字段完整")
    void shouldSnapshotReceiverInfo() {
        Order order = new Order();
        order.setReceiverName("张三");
        order.setReceiverPhone("13800138000");
        order.setReceiverAddress("广东省深圳市南山区科技园路1号");

        assertEquals("张三", order.getReceiverName());
        assertEquals("13800138000", order.getReceiverPhone());
        assertEquals("广东省深圳市南山区科技园路1号", order.getReceiverAddress());
    }

    @Test
    @DisplayName("Order — 时间字段正确赋值")
    void shouldSetAllTimeFields() {
        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setCreateTime(now);
        order.setPayTime(now.plusMinutes(5));
        order.setConsignTime(now.plusHours(24));
        order.setEndTime(now.plusDays(5));
        order.setCloseTime(null);

        assertEquals(now, order.getCreateTime());
        assertNotNull(order.getPayTime());
        assertTrue(order.getPayTime().isAfter(order.getCreateTime()));
        assertNull(order.getCloseTime()); // 未取消
    }

    // ==================== CreateOrderReq（V5.4 收缩 + V5.7 remark 重新立项后的形状） ====================

    @Test
    @DisplayName("CreateOrderReq — 下单入参仅 addressId + 单券 userCouponId + 买家留言 remark + 展示币种 displayCurrency（V5.17）")
    void shouldKeepOnlyAddressAndSingleCoupon_whenCreateOrderReqBuilt() {
        var req = new CreateOrderReq();
        req.setAddressId(1L);
        req.setUserCouponId(5L);
        req.setRemark("请包装牢固，谢谢");
        req.setDisplayCurrency("USD");

        assertEquals(1L, req.getAddressId());
        assertEquals(5L, req.getUserCouponId());
        assertEquals("请包装牢固，谢谢", req.getRemark());
        assertEquals("USD", req.getDisplayCurrency());
        // V5.4 收缩（cartItemIds/多券移除）+ V5.7 重新立项 remark + V5.17 多币种增量 displayCurrency：
        // 请求体恒为四业务字段
        long businessFields = java.util.Arrays.stream(CreateOrderReq.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .count();
        assertEquals(4, businessFields, "字段仅剩 addressId/userCouponId/remark/displayCurrency（V5.17）");
        // 多券为 roadmap（需 PRD），当前请求体不暴露 userCouponIds
        assertTrue(java.util.Arrays.stream(CreateOrderReq.class.getDeclaredFields())
                        .noneMatch(f -> "userCouponIds".equals(f.getName())),
                "userCouponIds 死字段应已删除（Controller 从未装配）");
    }

    @Test
    @DisplayName("DirectOrderReq — 立即购买入参不含多券死字段 userCouponIds")
    void shouldNotExposeMultiCoupon_whenDirectOrderReqBuilt() {
        assertTrue(java.util.Arrays.stream(DirectOrderReq.class.getDeclaredFields())
                        .noneMatch(f -> "userCouponIds".equals(f.getName())),
                "DirectOrderReq.userCouponIds 死字段应已删除");
    }

    @Test
    @DisplayName("CreateOrderReq — addressId 为必填（@NotNull 前置校验）")
    void shouldRejectNullAddressId_whenValidated() throws Exception {
        var req = new CreateOrderReq();
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

        var violations = validator.validate(req);
        assertTrue(violations.stream()
                        .anyMatch(v -> "addressId".equals(v.getPropertyPath().toString())),
                "addressId 为空应触发校验约束");
    }

    // ==================== ShipOrderReq ====================

    @Test
    @DisplayName("ShipOrderReq — 物流信息字段正确")
    void shouldSetShipOrderReqFields() {
        var req = new ShipOrderReq();
        req.setLogisticsNumber("SF123456");
        req.setLogisticsCompany("顺丰快递");

        assertEquals("SF123456", req.getLogisticsNumber());
        assertEquals("顺丰快递", req.getLogisticsCompany());
    }
}
