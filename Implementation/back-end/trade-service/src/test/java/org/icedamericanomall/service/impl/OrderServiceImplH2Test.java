package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.client.LogisticsClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.client.UserClient;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.domain.entity.OrderItemEntity;
import org.icedamericanomall.enums.OrderStatusEnum;
import org.icedamericanomall.mapper.OrderItemMapper;
import org.icedamericanomall.mapper.OrderMapper;
import org.icedamericanomall.service.OrderService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * H2 集成测试 — 验证 OrderServiceImpl 的 lambdaQuery/lambdaUpdate 链。
 * 不使用 Spring Boot 上下文（无需 Nacos/Redis/Feign），直接构建 H2 + MyBatis-Plus。
 */
@DisplayName("OrderServiceImpl H2 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderServiceImplH2Test {

    private static OrderService orderService;

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
                    order_no VARCHAR(64) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    seller_id BIGINT NOT NULL,
                    total_amount INT DEFAULT 0,
                    pay_amount INT DEFAULT 0,
                    discount_amount INT DEFAULT 0,
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
                    sku_id BIGINT NOT NULL,
                    product_name VARCHAR(100),
                    sku_spec VARCHAR(200),
                    price INT NOT NULL,
                    quantity INT NOT NULL,
                    sub_total INT NOT NULL,
                    image VARCHAR(255),
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

        OrderItemMapper orderItemMapper = sqlSessionFactory.openSession().getMapper(OrderItemMapper.class);
        OrderServiceImpl impl = new OrderServiceImpl(orderItemMapper);
        ReflectionTestUtils.setField(impl, "baseMapper",
                sqlSessionFactory.openSession().getMapper(OrderMapper.class));

        orderService = impl;
    }

    private void saveOrder(String orderNo, Long userId, Long sellerId, int status) {
        OrderEntity o = new OrderEntity();
        o.setOrderNo(orderNo); o.setUserId(userId); o.setSellerId(sellerId);
        o.setTotalAmount(129900); o.setPayAmount(129900);
        o.setStatus(status); o.setReceiverName("Test"); o.setReceiverPhone("13800138000");
        o.setReceiverAddress("广东省深圳市"); o.setVersion(0);
        orderService.save(o);
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("getByOrderNo — 根据订单号查询")
    void shouldFindOrderByOrderNo() {
        saveOrder("ORD-H2-001", 100L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());
        OrderEntity found = orderService.getByOrderNo("ORD-H2-001");
        assertNotNull(found);
        assertEquals(100L, found.getUserId());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("getByOrderNo — 不存在返回 null")
    void shouldReturnNull_whenNotFound() {
        assertNull(orderService.getByOrderNo("NONEXISTENT"));
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("createOrderWithItems — 事务中创建订单+订单项")
    void shouldCreateOrderAndItemsAtomically() {
        OrderEntity order = new OrderEntity();
        order.setOrderNo("ORD-H2-003"); order.setUserId(900L); order.setSellerId(1L);
        order.setTotalAmount(50000); order.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());

        OrderItemEntity item = new OrderItemEntity();
        item.setSkuId(100L); item.setPrice(50000); item.setQuantity(1); item.setSubTotal(50000);

        orderService.createOrderWithItems(order, List.of(item));

        assertNotNull(order.getId());
        assertEquals(order.getId(), item.getOrderId());

        OrderEntity found = orderService.getByOrderNo("ORD-H2-003");
        assertNotNull(found);
        assertEquals(900L, found.getUserId());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("confirmReceipt — 待收货→已完成")
    void shouldCompleteOrder_whenConfirmReceipt() {
        saveOrder("ORD-H2-C1", 400L, 1L, OrderStatusEnum.PENDING_RECEIPT.getCode());
        orderService.confirmReceipt("ORD-H2-C1", 400L);

        OrderEntity updated = orderService.getByOrderNo("ORD-H2-C1");
        assertEquals(OrderStatusEnum.COMPLETED.getCode(), updated.getStatus());
        assertNotNull(updated.getEndTime());
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("confirmReceipt — 非待收货状态拒绝")
    void shouldRejectConfirm_whenNotPendingReceipt() {
        saveOrder("ORD-H2-C2", 400L, 1L, OrderStatusEnum.PENDING_PAYMENT.getCode());
        assertThrows(Exception.class, () ->
                orderService.confirmReceipt("ORD-H2-C2", 400L));
    }

}
