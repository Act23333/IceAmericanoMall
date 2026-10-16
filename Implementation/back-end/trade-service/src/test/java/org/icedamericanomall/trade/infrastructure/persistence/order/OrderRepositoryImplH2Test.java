package org.icedamericanomall.trade.infrastructure.persistence.order;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.domain.model.order.ShipPackageResult;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2 集成测试 — 验证 OrderRepositoryImpl 的 lambdaQuery/lambdaUpdate 链与条件更新状态机。
 * 不使用 Spring Boot 上下文（无需 Nacos/Redis/Feign），直接构建 H2 + MyBatis-Plus。
 */
@DisplayName("OrderRepositoryImpl H2 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderRepositoryImplH2Test {
    /** SKU 业务键（32-hex，ADR-061 §决策 2：线格式与订单项标识一律业务键）。 */
    private static final String SKU_BUSINESS_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String SKU_BUSINESS_ID_2 = "112233445566778899aabbccddeeff00";
    private static final String SPU_BUSINESS_ID = "aabbccddeeff00112233445566778899";

    private static OrderRepositoryImpl repository;

    @BeforeAll
    static void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");

        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE orders (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    order_no BINARY(16) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    seller_id BIGINT NOT NULL,
                    total_amount INT DEFAULT 0,
                    pay_amount INT DEFAULT 0,
                    discount_amount INT DEFAULT 0,
                    post_fee INT DEFAULT 0,
                    remark VARCHAR(200),
                    display_currency VARCHAR(3),
                    display_rate BIGINT,
                    status INT DEFAULT 1,
                    order_type INT DEFAULT 1,
                    payment_type INT DEFAULT 1,
                    receiver_name VARCHAR(50),
                    receiver_phone VARCHAR(20),
                    receiver_address VARCHAR(200),
                    version INT DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    pay_time TIMESTAMP NULL,
                    consign_time TIMESTAMP NULL,
                    end_time TIMESTAMP NULL,
                    close_time TIMESTAMP NULL,
                    comment_time TIMESTAMP NULL,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            stmt.execute("""
                CREATE TABLE order_item (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    order_id BIGINT NOT NULL,
                    sku_id BINARY(16) NOT NULL,
                    spu_id BINARY(16) NOT NULL,
                    spu_name VARCHAR(100),
                    sku_spec VARCHAR(200),
                    price INT NOT NULL,
                    quantity INT NOT NULL,
                    sub_total INT NOT NULL,
                    image VARCHAR(255),
                    package_no INT DEFAULT NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(OrderMapper.class);
        config.addMapper(OrderItemMapper.class);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        repository = new OrderRepositoryImpl(
                sqlSessionFactory.openSession().getMapper(OrderMapper.class),
                sqlSessionFactory.openSession().getMapper(OrderItemMapper.class));
    }

    @Test
    @DisplayName("findByOrderNo — 非 hex 垃圾入参（外部输入路径）→ 宽松解析落空返回 null，不抛异常不误命中")
    void shouldReturnNull_whenOrderNoIsGarbage() {
        saveOrder("aaaaaaaa0000700080000000000000ee", 100L, 9L, 1);
        assertNull(repository.findByOrderNo("NOT-A-HEX-ORDER-NO"), "垃圾入参必须按未命中处理（ADR-050 toBytesOrNull）");
        assertNull(repository.findByOrderNo("123456"), "长度不足同样未命中");
    }

    private void saveOrder(String orderNo, Long userId, Long sellerId, int status) {
        Order o = new Order();
        o.setOrderNo(orderNo); o.setUserId(userId); o.setSellerId(sellerId);
        o.setTotalAmount(129900); o.setPayAmount(129900);
        o.setStatus(status); o.setReceiverName("Test"); o.setReceiverPhone("13800138000");
        o.setReceiverAddress("广东省深圳市"); o.setVersion(0);
        repository.saveWithItems(o, List.of());
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("findByOrderNo — 根据订单号查询")
    void shouldFindOrderByOrderNo() {
        saveOrder("aaaaaaaa000070008000000000000001", 100L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());
        Order found = repository.findByOrderNo("aaaaaaaa000070008000000000000001");
        assertNotNull(found);
        assertEquals(100L, found.getUserId());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("findByOrderNo — 不存在返回 null")
    void shouldReturnNull_whenNotFound() {
        assertNull(repository.findByOrderNo("aaaaaaaa00007000800000000000ffff"));
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("saveWithItems — 事务中创建订单+订单项")
    void shouldCreateOrderAndItemsAtomically() {
        Order order = new Order();
        order.setOrderNo("aaaaaaaa000070008000000000000002"); order.setUserId(900L); order.setSellerId(1L);
        order.setTotalAmount(50000); order.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());

        OrderItem item = new OrderItem();
        item.setSkuId(SKU_A); item.setSpuId(SPU_BUSINESS_ID); item.setSpuName("商品A");
        item.setPrice(50000); item.setQuantity(1); item.setSubTotal(50000); item.setSkuSpec("规格");

        repository.saveWithItems(order, List.of(item));

        assertNotNull(order.getId());
        assertEquals(order.getId(), item.getOrderId());

        Order found = repository.findByOrderNo("aaaaaaaa000070008000000000000002");
        assertNotNull(found);
        assertEquals(900L, found.getUserId());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("confirmReceiptByUser — 待收货→已完成")
    void shouldCompleteOrder_whenConfirmReceipt() {
        saveOrder("aaaaaaaa000070008000000000000003", 400L, 1L, OrderStatusEnum.PENDING_RECEIPT.getCode());
        repository.confirmReceiptByUser("aaaaaaaa000070008000000000000003", 400L);

        Order updated = repository.findByOrderNo("aaaaaaaa000070008000000000000003");
        assertEquals(OrderStatusEnum.COMPLETED.getCode(), updated.getStatus());
        assertNotNull(updated.getEndTime());
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("confirmReceiptByUser — 非待收货状态拒绝")
    void shouldRejectConfirm_whenNotPendingReceipt() {
        saveOrder("aaaaaaaa000070008000000000000004", 400L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());
        assertThrows(Exception.class, () ->
                repository.confirmReceiptByUser("aaaaaaaa000070008000000000000004", 400L));
    }

    // ==================== 条件更新（并发安全） ====================

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("cancelByUser — 待付款订单条件更新为已取消")
    void shouldCancelOrder_whenConditionalUpdateHits() {
        saveOrder("aaaaaaaa000070008000000000000005", 500L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());

        repository.cancelByUser("aaaaaaaa000070008000000000000005", 500L);

        Order updated = repository.findByOrderNo("aaaaaaaa000070008000000000000005");
        assertEquals(OrderStatusEnum.CANCELLED.getCode(), updated.getStatus());
        assertNotNull(updated.getCloseTime());
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("cancelByUser — 已取消订单重复取消：0 行受影响抛 ORDER_STATUS_INVALID")
    void shouldRejectCancel_whenAlreadyCancelled() {
        saveOrder("aaaaaaaa000070008000000000000006", 500L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());
        repository.cancelByUser("aaaaaaaa000070008000000000000006", 500L);

        BizException ex = assertThrows(BizException.class, () ->
                repository.cancelByUser("aaaaaaaa000070008000000000000006", 500L));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getErrorCode());
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("closeTimeoutOrder — 首次命中返回 true，重复调用返回 false（幂等无操作）")
    void shouldReturnFalse_whenCloseTimeoutOrderAlreadyCancelled() {
        saveOrder("aaaaaaaa000070008000000000000020", 600L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());
        Order order = repository.findByOrderNo("aaaaaaaa000070008000000000000020");

        assertTrue(repository.closeTimeoutOrder(order.getId()));
        assertFalse(repository.closeTimeoutOrder(order.getId()));
    }

    // ==================== 支付回写状态机 ====================

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("transitionFromPendingPayment — 待付款→待发货（支付成功）")
    void shouldTransitionToPendingShipment_whenPaidCallback() {
        saveOrder("aaaaaaaa000070008000000000000010", 700L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());

        repository.transitionFromPendingPayment("aaaaaaaa000070008000000000000010", OrderStatusEnum.PENDING_SHIPMENT.getCode());

        Order updated = repository.findByOrderNo("aaaaaaaa000070008000000000000010");
        assertEquals(OrderStatusEnum.PENDING_SHIPMENT.getCode(), updated.getStatus());
        assertNotNull(updated.getPayTime());
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("transitionFromPendingPayment — 待付款→已取消（支付超时）")
    void shouldTransitionToCancelled_whenPayTimeout() {
        saveOrder("aaaaaaaa000070008000000000000011", 700L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());

        repository.transitionFromPendingPayment("aaaaaaaa000070008000000000000011", OrderStatusEnum.CANCELLED.getCode());

        assertEquals(OrderStatusEnum.CANCELLED.getCode(),
                repository.findByOrderNo("aaaaaaaa000070008000000000000011").getStatus());
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("transitionFromPendingPayment — 重复回调：非待付款源状态抛 ORDER_STATUS_INVALID")
    void shouldRejectRepeatCallback_whenAlreadyTransitioned() {
        saveOrder("aaaaaaaa000070008000000000000012", 700L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());
        repository.transitionFromPendingPayment("aaaaaaaa000070008000000000000012", OrderStatusEnum.PENDING_SHIPMENT.getCode());

        BizException ex = assertThrows(BizException.class, () -> repository
                .transitionFromPendingPayment("aaaaaaaa000070008000000000000012", OrderStatusEnum.PENDING_SHIPMENT.getCode()));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getErrorCode());
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("transitionFromPendingPayment — 非法目标状态（已完成）抛 ORDER_STATUS_INVALID 且状态不变")
    void shouldRejectIllegalTargetStatus_whenPaymentCallback() {
        saveOrder("aaaaaaaa000070008000000000000013", 700L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());

        BizException ex = assertThrows(BizException.class, () -> repository
                .transitionFromPendingPayment("aaaaaaaa000070008000000000000013", OrderStatusEnum.COMPLETED.getCode()));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getErrorCode());
        assertEquals(OrderStatusEnum.PENDING_PAYMENT.getCode(),
                repository.findByOrderNo("aaaaaaaa000070008000000000000013").getStatus());
    }

    // ==================== 归属校验 ====================

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("findOwnedOrder — 订单不存在抛 ORDER_NOT_FOUND")
    void shouldThrowOrderNotFound_whenOwnedOrderMissing() {
        BizException ex = assertThrows(BizException.class, () ->
                repository.findOwnedOrder("aaaaaaaa000070008000000000000007", 800L));
        assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("findOwnedOrder — 非本人订单抛 FORBIDDEN")
    void shouldThrowForbidden_whenOrderBelongsToAnotherUser() {
        saveOrder("aaaaaaaa000070008000000000000008", 800L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                repository.findOwnedOrder("aaaaaaaa000070008000000000000008", 999L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @org.junit.jupiter.api.Order(16)
    @DisplayName("findSellerOrder — 非本店订单抛 FORBIDDEN")
    void shouldThrowForbidden_whenOrderBelongsToAnotherSeller() {
        saveOrder("aaaaaaaa000070008000000000000009", 800L, 7L, OrderStatusEnum.PENDING_PAYMENT.getCode());

        assertThrows(ForbiddenException.class, () ->
                repository.findSellerOrder("aaaaaaaa000070008000000000000009", 8L));
    }

    @Test
    @org.junit.jupiter.api.Order(17)
    @DisplayName("shipPackage — 非待发货状态拒绝（V5.6 拆单发货入口）")
    void shouldRejectShip_whenStatusNotPendingShipment() {
        saveOrder("aaaaaaaa000070008000000000000014", 810L, 9L, OrderStatusEnum.PENDING_PAYMENT.getCode());

        BizException ex = assertThrows(BizException.class, () ->
                repository.shipPackage("aaaaaaaa000070008000000000000014", 9L, null));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getErrorCode());
    }

    // ==================== V5.6 拆单发货（F3 BDD 场景，H2 真实库往返） ====================

    private static final String SKU_A = "aaaaaaaa00007001000000000000aaaa";
    private static final String SKU_B = "bbbbbbbb00007002000000000000bbbb";

    /** 建「待发货 + 两条明细」订单，返回订单号。 */
    private String savePendingShipmentOrderWithItems(String orderNo, String... skuIds) {
        Order o = new Order();
        o.setOrderNo(orderNo); o.setUserId(810L); o.setSellerId(9L);
        o.setTotalAmount(2000); o.setPayAmount(2000); o.setPostFee(0);
        o.setStatus(OrderStatusEnum.PENDING_SHIPMENT.getCode());
        o.setReceiverName("Test"); o.setReceiverPhone("13800138000");
        o.setReceiverAddress("广东省深圳市"); o.setVersion(0);
        List<OrderItem> items = new java.util.ArrayList<>();
        for (String skuId : skuIds) {
            OrderItem item = new OrderItem();
            item.setSkuId(skuId); item.setSpuId(SPU_BUSINESS_ID); item.setSpuName("商品" + skuId); item.setSkuSpec("规格");
            item.setPrice(1000); item.setQuantity(1); item.setSubTotal(1000);
            items.add(item);
        }
        repository.saveWithItems(o, items);
        return orderNo;
    }

    private OrderItem itemOf(String orderNo, String skuId) {
        Order order = repository.findByOrderNo(orderNo);
        return repository.findItemsByOrderId(order.getId()).stream()
                .filter(i -> java.util.Objects.equals(i.getSkuId(), skuId))
                .findFirst().orElseThrow();
    }

    @Test
    @org.junit.jupiter.api.Order(18)
    @DisplayName("F3-1 一单两包裹先后发货 — 部分发货保持待发货(2)，全量发完转待收货(3)")
    void shouldKeepPendingShipment_thenMoveToPendingReceipt_whenShippingTwoPackages() {
        String orderNo = savePendingShipmentOrderWithItems("aaaaaaaa000070008000000000000015", SKU_A, SKU_B);

        // When 第一次发货本包裹 skuIds=[sku1]
        ShipPackageResult first = repository.shipPackage(orderNo, 9L, List.of(SKU_A));

        // Then 订单仍为待发货(2)，order_item(sku1).packageNo=1，包裹号=1，未全部发完
        assertEquals(1, first.packageNo());
        assertFalse(first.allShipped());
        assertEquals(OrderStatusEnum.PENDING_SHIPMENT.getCode(),
                repository.findByOrderNo(orderNo).getStatus());
        assertEquals(1, itemOf(orderNo, SKU_A).getPackageNo());
        assertNull(itemOf(orderNo, SKU_B).getPackageNo());
        assertNotNull(repository.findByOrderNo(orderNo).getConsignTime(), "首包应写发货时间");

        // When 第二次发货 skuIds=[sku2]
        ShipPackageResult second = repository.shipPackage(orderNo, 9L, List.of(SKU_B));

        // Then 全部明细已发货，订单迁移为待收货(3)，包裹号=2
        assertEquals(2, second.packageNo());
        assertTrue(second.allShipped());
        assertEquals(OrderStatusEnum.PENDING_RECEIPT.getCode(),
                repository.findByOrderNo(orderNo).getStatus());
        assertEquals(2, itemOf(orderNo, SKU_B).getPackageNo());
    }

    @Test
    @org.junit.jupiter.api.Order(19)
    @DisplayName("F3-2 空 skuIds 全量发货 — 全部明细打同一 packageNo=1，直接转待收货(3)")
    void shouldShipAllItemsInOnePackage_whenSkuIdsEmpty() {
        String orderNo = savePendingShipmentOrderWithItems("aaaaaaaa000070008000000000000016", SKU_A, SKU_B);

        ShipPackageResult result = repository.shipPackage(orderNo, 9L, null);

        assertEquals(1, result.packageNo());
        assertTrue(result.allShipped());
        assertEquals(1, itemOf(orderNo, SKU_A).getPackageNo());
        assertEquals(1, itemOf(orderNo, SKU_B).getPackageNo());
        assertEquals(OrderStatusEnum.PENDING_RECEIPT.getCode(),
                repository.findByOrderNo(orderNo).getStatus());
    }

    @Test
    @org.junit.jupiter.api.Order(20)
    @DisplayName("F3-3 重复发货同一 sku 被拒 — ORDER_STATUS_INVALID(3010)，订单状态不变")
    void shouldRejectDuplicateShip_whenSkuAlreadyPackaged() {
        String orderNo = savePendingShipmentOrderWithItems("aaaaaaaa000070008000000000000017", SKU_A, SKU_B);
        repository.shipPackage(orderNo, 9L, List.of(SKU_A));

        BizException ex = assertThrows(BizException.class, () ->
                repository.shipPackage(orderNo, 9L, List.of(SKU_A)));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getErrorCode());
        assertEquals(OrderStatusEnum.PENDING_SHIPMENT.getCode(),
                repository.findByOrderNo(orderNo).getStatus());
        assertEquals(1, itemOf(orderNo, SKU_A).getPackageNo(), "包裹归属不应被重复标记");
    }

    @Test
    @org.junit.jupiter.api.Order(21)
    @DisplayName("F3-4 发货明细校验归属 — 含他单 skuId → PARAM_ERROR(1101)，无任何状态变更")
    void shouldRejectShip_whenSkuNotBelongToOrder() {
        String orderNo = savePendingShipmentOrderWithItems("aaaaaaaa000070008000000000000018", SKU_A, SKU_B);
        // 另一订单的明细 sku
        String otherOrderNo = savePendingShipmentOrderWithItems("aaaaaaaa000070008000000000000019", "cccccccc00008001000000000000cccc");

        BizException ex = assertThrows(BizException.class, () ->
                repository.shipPackage(orderNo, 9L, List.of(SKU_A, "cccccccc00008001000000000000cccc")));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertEquals(OrderStatusEnum.PENDING_SHIPMENT.getCode(),
                repository.findByOrderNo(orderNo).getStatus());
        assertNull(itemOf(orderNo, SKU_A).getPackageNo(), "校验失败时不得标记任何明细");
        assertNull(itemOf(otherOrderNo, "cccccccc00008001000000000000cccc").getPackageNo(), "他单明细不受影响");
    }

}
