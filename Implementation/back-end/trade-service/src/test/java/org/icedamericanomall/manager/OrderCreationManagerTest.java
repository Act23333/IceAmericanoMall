package org.icedamericanomall.manager;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.CartClient;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.convert.OrderConverterImpl;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.domain.vo.OrderVO;
import org.icedamericanomall.dto.AddressDTO;
import org.icedamericanomall.dto.CartItemDTO;
import org.icedamericanomall.dto.SkuDTO;
import org.icedamericanomall.enums.OrderStatusEnum;
import org.icedamericanomall.enums.OrderTypeEnum;
import org.icedamericanomall.mapper.OrderItemMapper;
import org.icedamericanomall.mapper.OrderMapper;
import org.icedamericanomall.service.OrderService;
import org.icedamericanomall.service.impl.OrderServiceImpl;
import org.icedamericanomall.strategy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.noLazy.common.exception.BizException;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * V4.1: OrderCreationManager 下单编排集成测试（策略模式）。
 * 使用真实策略实例 + Mock Feign 客户端，验证统一订单中心管道。
 */
@DisplayName("OrderCreationManager 下单编排集成测试")
class OrderCreationManagerTest {

    private OrderService orderService;
    private CartClient cartClient;
    private AddressClient addressClient;
    private SkuClient skuClient;
    private CouponClient couponClient;
    private OrderCreationManager orderManager;

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
                    status INT DEFAULT 1, order_type INT DEFAULT 1, payment_type INT DEFAULT 1,
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

        // V4.1: 构造真实策略实例 + 策略工厂（Spring 注入模拟）
        NormalCartOrderStrategy normalStrategy = new NormalCartOrderStrategy(
                cartClient, skuClient, addressClient, couponClient);
        DirectOrderStrategy directStrategy = new DirectOrderStrategy(skuClient, addressClient, couponClient);
        FlashSaleOrderStrategy flashSaleStrategy = new FlashSaleOrderStrategy(skuClient, addressClient);
        OrderCreateStrategyFactory factory = new OrderCreateStrategyFactory(
                List.of(normalStrategy, directStrategy, flashSaleStrategy));

        this.orderManager = new OrderCreationManager(orderService, new OrderConverterImpl(),
                skuClient, couponClient,
                mock(org.icedamericanomall.producer.OrderTimeoutPublisher.class), factory);
    }

    private CartItemDTO cartItem(Long cartItemId, Long skuId, int qty) {
        CartItemDTO c = new CartItemDTO();
        c.setCartItemId(cartItemId); c.setSkuId(skuId); c.setQuantity(qty);
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

    private OrderCreateContext ctx(OrderTypeEnum type) {
        OrderCreateContext c = new OrderCreateContext();
        c.setUserId(100L);
        c.setOrderType(type);
        c.setAddressId(1L);
        return c;
    }

    @Test
    @DisplayName("createOrder(NORMAL) — 正常购物车下单：金额正确、地址快照、扣库存、删已购项")
    void shouldCreateOrder_whenValidCartAndAddress() {
        when(cartClient.getSelectedItems(100L)).thenReturn(
                List.of(cartItem(1L, 1000L, 2), cartItem(2L, 1001L, 1)));
        when(skuClient.getSkuListByIds(anyList()))
                .thenReturn(List.of(sku(1000L, 9L, 5000, 10), sku(1001L, 9L, 3000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());

        OrderCreateContext c = ctx(OrderTypeEnum.NORMAL);
        OrderVO vo = orderManager.createOrder(c);

        assertNotNull(vo);
        assertNotNull(vo.getOrderNo());
        assertEquals(OrderTypeEnum.NORMAL.getCode(), vo.getOrderType());
        assertEquals(2, vo.getItems().size());

        OrderEntity saved = orderService.getByOrderNo(vo.getOrderNo());
        assertNotNull(saved);
        assertEquals(5000 * 2 + 3000, saved.getTotalAmount());
        assertEquals(saved.getTotalAmount(), saved.getPayAmount());
        assertEquals(0, saved.getDiscountAmount());
        assertEquals(9L, saved.getSellerId());
        assertEquals(OrderStatusEnum.PENDING_PAYMENT.getCode(), saved.getStatus());
        assertEquals("张三", saved.getReceiverName());
        assertTrue(saved.getReceiverAddress().contains("深圳市"));

        verify(skuClient).deductStock(anyList());
        verify(cartClient).deleteByIds(eq(100L), anyList());
        verify(cartClient, never()).clearCart(anyLong());
    }

    @Test
    @DisplayName("createOrder(NORMAL) — 使用优惠券：抵扣后 payAmount = total - discount")
    void shouldApplyCouponDiscount_whenCouponProvided() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());
        when(couponClient.useCoupon(eq(100L), eq(55L), anyString(), eq(10000), anyInt(), anyLong())).thenReturn(3000);

        OrderCreateContext c = ctx(OrderTypeEnum.NORMAL);
        c.setUserCouponId(55L);
        OrderVO vo = orderManager.createOrder(c);

        OrderEntity saved = orderService.getByOrderNo(vo.getOrderNo());
        assertEquals(10000, saved.getTotalAmount());
        assertEquals(3000, saved.getDiscountAmount());
        assertEquals(7000, saved.getPayAmount());
        verify(couponClient).useCoupon(eq(100L), eq(55L), anyString(), eq(10000), anyInt(), anyLong());
    }

    @Test
    @DisplayName("createOrder(NORMAL) — 库存不足：拒绝下单且不扣库存")
    void shouldReject_whenStockInsufficient() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 5)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 3)));

        assertThrows(BizException.class, () -> orderManager.createOrder(ctx(OrderTypeEnum.NORMAL)));
        verify(skuClient, never()).deductStock(anyList());
    }

    @Test
    @DisplayName("createOrder(NORMAL) — 空购物车：拒绝下单")
    void shouldReject_whenCartEmpty() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of());
        assertThrows(BizException.class, () -> orderManager.createOrder(ctx(OrderTypeEnum.NORMAL)));
    }

    @Test
    @DisplayName("createOrder(NORMAL) — 跨店铺：拒绝下单")
    void shouldReject_whenMultipleSellers() {
        when(cartClient.getSelectedItems(100L)).thenReturn(
                List.of(cartItem(1L, 1000L, 1), cartItem(2L, 1001L, 1)));
        when(skuClient.getSkuListByIds(anyList()))
                .thenReturn(List.of(sku(1000L, 9L, 5000, 10), sku(1001L, 8L, 3000, 10)));
        assertThrows(BizException.class, () -> orderManager.createOrder(ctx(OrderTypeEnum.NORMAL)));
        verify(skuClient, never()).deductStock(anyList());
    }

    @Test
    @DisplayName("createOrder — 建单失败触发 Saga：回滚已扣库存")
    void shouldRestoreStock_whenOrderCreationFails() {
        OrderService failingService = mock(OrderService.class);
        doThrow(new RuntimeException("DB down")).when(failingService)
                .createOrderWithItems(any(), anyList());

        NormalCartOrderStrategy normalStrategy = new NormalCartOrderStrategy(
                cartClient, skuClient, addressClient, couponClient);
        OrderCreateStrategyFactory factory = new OrderCreateStrategyFactory(List.of(normalStrategy));
        OrderCreationManager mgr = new OrderCreationManager(failingService, new OrderConverterImpl(),
                skuClient, couponClient,
                mock(org.icedamericanomall.producer.OrderTimeoutPublisher.class), factory);

        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());

        assertThrows(BizException.class, () -> mgr.createOrder(ctx(OrderTypeEnum.NORMAL)));
        verify(skuClient).deductStock(anyList());
        verify(skuClient).restoreStock(anyList());
    }

    @Test
    @DisplayName("createOrder(DIRECT) — 立即购买：不操作购物车")
    void shouldNotTouchCart_whenDirectOrder() {
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());

        OrderCreateContext c = ctx(OrderTypeEnum.DIRECT);
        c.setSkuId(1000L);
        c.setQuantity(1);
        OrderVO vo = orderManager.createOrder(c);

        assertNotNull(vo.getOrderNo());
        assertEquals(OrderTypeEnum.DIRECT.getCode(), vo.getOrderType());
        verify(cartClient, never()).deleteByIds(anyLong(), anyList());
        verify(cartClient, never()).clearCart(anyLong());
    }

    @Test
    @DisplayName("createOrder(FLASH_SALE) — 秒杀订单：使用闪购价且不操作购物车")
    void shouldUseFlashPrice_whenFlashSaleOrder() {
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());

        OrderCreateContext c = ctx(OrderTypeEnum.FLASH_SALE);
        c.setSkuId(1000L);
        c.setQuantity(1);
        c.setFlashId(99L);
        c.setFlashPrice(1000); // 秒杀价 10元，远低于 SKU 原价 50元
        OrderVO vo = orderManager.createOrder(c);

        assertNotNull(vo.getOrderNo());
        assertEquals(OrderTypeEnum.FLASH_SALE.getCode(), vo.getOrderType());
        OrderEntity saved = orderService.getByOrderNo(vo.getOrderNo());
        assertEquals(1000, saved.getTotalAmount()); // 秒杀价
        assertEquals(0, saved.getDiscountAmount()); // 秒杀不使用优惠券
        verify(cartClient, never()).deleteByIds(anyLong(), anyList());
    }
}
