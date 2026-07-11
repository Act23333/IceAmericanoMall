package org.icedAmericanoMall.manager;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedAmericanoMall.client.AddressClient;
import org.icedAmericanoMall.client.CartClient;
import org.icedAmericanoMall.client.CouponClient;
import org.icedAmericanoMall.client.LogisticsClient;
import org.icedAmericanoMall.client.SkuClient;
import org.icedAmericanoMall.client.UserClient;
import org.icedAmericanoMall.convert.OrderConverterImpl;
import org.icedAmericanoMall.domain.dto.CreateOrderReq;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.vo.OrderVO;
import org.icedAmericanoMall.dto.AddressDTO;
import org.icedAmericanoMall.dto.CartItemDTO;
import org.icedAmericanoMall.dto.SkuDTO;
import org.icedAmericanoMall.dto.StockOpDTO;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.icedAmericanoMall.mapper.OrderItemMapper;
import org.icedAmericanoMall.mapper.OrderMapper;
import org.icedAmericanoMall.service.OrderService;
import org.icedAmericanoMall.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.noLazy.common.exception.BizException;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OrderManager 下单编排集成测试 —— H2 承接真实 OrderService，Feign 客户端用 Mockito 打桩。
 * 锁定"购物车→SKU→地址快照→库存扣减→建单→清车"链路与 Saga 回滚，保护后续规范重构不回归。
 * <p>注：Feign 路径正确性由契约测试（Phase E）覆盖；此处聚焦编排行为。
 */
@DisplayName("OrderManager 下单编排集成测试")
class OrderManagerTest {

    private OrderService orderService;      // 真实实现 + H2
    private CartClient cartClient;
    private AddressClient addressClient;
    private SkuClient skuClient;
    private CouponClient couponClient;
    private OrderManager orderManager;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:ordermgr_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE orders (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    order_no VARCHAR(64) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL, seller_id BIGINT NOT NULL,
                    total_amount INT DEFAULT 0, pay_amount INT DEFAULT 0, discount_amount INT DEFAULT 0,
                    status INT DEFAULT 1, payment_type INT DEFAULT 1,
                    receiver_name VARCHAR(50), receiver_phone VARCHAR(20), receiver_address VARCHAR(200),
                    version INT DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, pay_time TIMESTAMP NULL,
                    consign_time TIMESTAMP NULL, end_time TIMESTAMP NULL, close_time TIMESTAMP NULL,
                    comment_time TIMESTAMP NULL, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            stmt.execute("""
                CREATE TABLE order_item (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
                    product_name VARCHAR(100), sku_spec VARCHAR(200), price INT NOT NULL,
                    quantity INT NOT NULL, sub_total INT NOT NULL, image VARCHAR(255),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
        this.orderService = impl;

        this.cartClient = mock(CartClient.class);
        this.addressClient = mock(AddressClient.class);
        this.skuClient = mock(SkuClient.class);
        this.couponClient = mock(CouponClient.class);
        this.orderManager = new OrderManager(orderService, new OrderConverterImpl(),
                cartClient, addressClient, skuClient,
                mock(LogisticsClient.class), mock(UserClient.class), couponClient);
    }

    private CartItemDTO cartItem(Long skuId, int qty) {
        CartItemDTO c = new CartItemDTO();
        c.setSkuId(skuId); c.setQuantity(qty);
        return c;
    }

    private SkuDTO sku(Long id, Long sellerId, int price, int stock) {
        SkuDTO s = new SkuDTO();
        s.setId(id); s.setSellerId(sellerId); s.setProductName("商品" + id);
        s.setSpec("规格"); s.setPrice(price); s.setStock(stock); s.setImage("img.png");
        return s;
    }

    private AddressDTO address() {
        AddressDTO a = new AddressDTO();
        a.setId(1L); a.setReceiver("张三"); a.setPhone("13800138000");
        a.setProvince("广东省"); a.setCity("深圳市"); a.setDistrict("南山区");
        a.setStreet("科技路"); a.setDetail("1号");
        return a;
    }

    private CreateOrderReq req() {
        CreateOrderReq r = new CreateOrderReq();
        r.setAddressId(1L);
        return r;
    }

    @Test
    @DisplayName("createOrder — 正常下单：金额=各项之和、地址快照、扣库存、清购物车、订单入库")
    void shouldCreateOrder_whenValidCartAndAddress() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1000L, 2), cartItem(1001L, 1)));
        when(skuClient.getSkuListByIds(anyList()))
                .thenReturn(List.of(sku(1000L, 9L, 5000, 10), sku(1001L, 9L, 3000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());

        OrderVO vo = orderManager.createOrder(100L, req());

        assertNotNull(vo);
        assertNotNull(vo.getOrderNo());
        assertEquals(2, vo.getItems().size());

        OrderEntity saved = orderService.getByOrderNo(vo.getOrderNo());
        assertNotNull(saved);
        assertEquals(5000 * 2 + 3000, saved.getTotalAmount());        // 总金额=Σ(price*qty)
        assertEquals(saved.getTotalAmount(), saved.getPayAmount());
        assertEquals(0, saved.getDiscountAmount());
        assertEquals(9L, saved.getSellerId());
        assertEquals(OrderStatusEnum.PENDING_PAYMENT.getCode(), saved.getStatus());
        assertEquals("张三", saved.getReceiverName());
        assertEquals("13800138000", saved.getReceiverPhone());
        assertTrue(saved.getReceiverAddress().contains("深圳市"));    // 地址快照拼接

        verify(skuClient).deductStock(anyList());                     // 扣减库存
        verify(cartClient).clearCart(100L);                           // 清空购物车
    }

    @Test
    @DisplayName("createOrder — 使用优惠券：抵扣后 payAmount = total - discount")
    void shouldApplyCouponDiscount_whenCouponProvided() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());
        when(couponClient.useCoupon(eq(100L), eq(55L), anyString(), eq(10000))).thenReturn(3000);

        CreateOrderReq r = req();
        r.setUserCouponId(55L);
        OrderVO vo = orderManager.createOrder(100L, r);

        OrderEntity saved = orderService.getByOrderNo(vo.getOrderNo());
        assertEquals(10000, saved.getTotalAmount());
        assertEquals(3000, saved.getDiscountAmount());   // 抵扣 3000 分
        assertEquals(7000, saved.getPayAmount());        // 应付 = 总额 - 抵扣
        verify(couponClient).useCoupon(eq(100L), eq(55L), anyString(), eq(10000));
    }

    @Test
    @DisplayName("createOrder — 库存不足：拒绝下单且不扣库存")
    void shouldReject_whenStockInsufficient() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1000L, 5)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 3)));

        assertThrows(BizException.class, () -> orderManager.createOrder(100L, req()));
        verify(skuClient, never()).deductStock(anyList());
    }

    @Test
    @DisplayName("createOrder — 空购物车：拒绝下单")
    void shouldReject_whenCartEmpty() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of());
        assertThrows(BizException.class, () -> orderManager.createOrder(100L, req()));
    }

    @Test
    @DisplayName("createOrder — 跨店铺：拒绝下单")
    void shouldReject_whenMultipleSellers() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1000L, 1), cartItem(1001L, 1)));
        when(skuClient.getSkuListByIds(anyList()))
                .thenReturn(List.of(sku(1000L, 9L, 5000, 10), sku(1001L, 8L, 3000, 10)));
        assertThrows(BizException.class, () -> orderManager.createOrder(100L, req()));
        verify(skuClient, never()).deductStock(anyList());
    }

    @Test
    @DisplayName("createOrder — 建单失败触发 Saga：回滚已扣库存")
    void shouldRestoreStock_whenOrderCreationFails() {
        OrderService failingService = mock(OrderService.class);
        doThrow(new RuntimeException("DB down")).when(failingService)
                .createOrderWithItems(any(), anyList());
        OrderManager mgr = new OrderManager(failingService, new OrderConverterImpl(),
                cartClient, addressClient, skuClient,
                mock(LogisticsClient.class), mock(UserClient.class), couponClient);

        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());

        assertThrows(BizException.class, () -> mgr.createOrder(100L, req()));
        verify(skuClient).deductStock(anyList());       // 已扣减
        verify(skuClient).restoreStock(anyList());      // Saga 补偿回滚
    }
}
