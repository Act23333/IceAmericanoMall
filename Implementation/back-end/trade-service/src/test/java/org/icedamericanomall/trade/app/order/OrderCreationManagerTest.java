package org.icedamericanomall.trade.app.order;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.CartClient;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.CurrencyClient;
import org.icedamericanomall.client.MemberClient;
import org.icedamericanomall.client.PromotionClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.trade.adapter.assembler.OrderConverterImpl;
import org.icedamericanomall.trade.adapter.response.OrderVO;
import org.icedamericanomall.trade.app.order.pricing.PricingService;
import org.icedamericanomall.trade.app.order.strategy.DirectOrderStrategy;
import org.icedamericanomall.trade.app.order.strategy.FlashSaleOrderStrategy;
import org.icedamericanomall.trade.app.order.strategy.FreightPostageResolver;
import org.icedamericanomall.trade.app.order.strategy.NormalCartOrderStrategy;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateContext;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateStrategy;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateStrategyFactory;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.AddressCO;
import org.icedamericanomall.dto.CartItemCO;
import org.icedamericanomall.dto.MemberLevelCO;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.trade.infrastructure.messaging.DomainEventProducer;
import org.icedamericanomall.trade.infrastructure.messaging.OrderTimeoutPublisher;
import org.icedamericanomall.trade.infrastructure.persistence.order.OrderItemMapper;
import org.icedamericanomall.trade.infrastructure.persistence.order.OrderMapper;
import org.icedamericanomall.trade.infrastructure.persistence.order.OrderRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderCreationManager 下单编排集成测试（策略模式）。
 * 使用真实策略实例 + 真实仓储实现（H2）+ Mock Feign 客户端，验证统一订单中心管道与 Saga 补偿。
 */
@DisplayName("OrderCreationManager 下单编排集成测试")
class OrderCreationManagerTest {

    private OrderRepository orderRepository;
    private CartClient cartClient;
    private AddressClient addressClient;
    private SkuClient skuClient;
    private CouponClient couponClient;
    private PromotionClient promotionClient;
    private MemberClient memberClient;
    private OrderCreationManager orderManager;
    private FreightPostageResolver freightPostageResolver;

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
                    order_no BINARY(16) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL, seller_id BIGINT NOT NULL,
                    total_amount INT DEFAULT 0, pay_amount INT DEFAULT 0, discount_amount INT DEFAULT 0,
                    post_fee INT DEFAULT 0,
                    remark VARCHAR(200),
                    display_currency VARCHAR(3), display_rate BIGINT,
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
                    quantity INT NOT NULL, sub_total INT NOT NULL, image VARCHAR(255), package_no INT DEFAULT NULL,
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

        this.orderRepository = new OrderRepositoryImpl(
                sqlSessionFactory.openSession().getMapper(OrderMapper.class),
                sqlSessionFactory.openSession().getMapper(OrderItemMapper.class));

        this.cartClient = mock(CartClient.class);
        this.addressClient = mock(AddressClient.class);
        this.skuClient = mock(SkuClient.class);
        this.couponClient = mock(CouponClient.class);
        // V5.8（F5 价格计算中心）：默认桩使促销/会员两源无优惠（mock 默认空集合/null），
        // 既有用例聚焦下单管道断言；促销/会员叠加用例显式打桩。
        this.promotionClient = mock(PromotionClient.class);
        this.memberClient = mock(MemberClient.class);
        PricingService pricingService = new PricingService(promotionClient, memberClient, couponClient);
        // V5.6（F2 运费模型）：默认桩使运费=0，聚焦既有下单管道断言
        this.freightPostageResolver = mock(FreightPostageResolver.class);

        // V4.1: 构造真实策略实例 + 策略工厂（Spring 注入模拟）
        NormalCartOrderStrategy normalStrategy = new NormalCartOrderStrategy(
                cartClient, skuClient, addressClient, pricingService, freightPostageResolver);
        DirectOrderStrategy directStrategy = new DirectOrderStrategy(
                skuClient, addressClient, pricingService, freightPostageResolver);
        FlashSaleOrderStrategy flashSaleStrategy = new FlashSaleOrderStrategy(skuClient, addressClient);
        OrderCreateStrategyFactory factory = new OrderCreateStrategyFactory(
                List.of(normalStrategy, directStrategy, flashSaleStrategy));

        this.orderManager = newManager(orderRepository, factory);
    }

    private OrderCreationManager newManager(OrderRepository repo, OrderCreateStrategyFactory factory) {
        return new OrderCreationManager(repo, new OrderConverterImpl(),
                skuClient, couponClient,
                mock(OrderTimeoutPublisher.class),
                factory,
                mock(DomainEventProducer.class),
                new OrderDisplaySnapshotService(mock(CurrencyClient.class)));
    }

    private CartItemCO cartItem(Long cartItemId, Long skuId, int qty) {
        CartItemCO c = new CartItemCO();
        c.setCartItemId(cartItemId); c.setSkuId(skuId); c.setQuantity(qty);
        return c;
    }

    private SkuCO sku(Long id, Long sellerId, int price, int stock) {
        SkuCO s = new SkuCO();
        s.setId(id); s.setSellerId(sellerId); s.setProductName("商品" + id);
        s.setSpec("规格"); s.setPrice(price); s.setStock(stock); s.setImage("img.png");
        return s;
    }

    private AddressCO address() {
        AddressCO a = new AddressCO();
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

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
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
        when(couponClient.useCoupon(eq(100L), eq(55L), anyString(), eq(10000),
                anyInt(), anyLong(), nullable(String.class), nullable(String.class))).thenReturn(3000);

        OrderCreateContext c = ctx(OrderTypeEnum.NORMAL);
        c.setUserCouponId(55L);
        OrderVO vo = orderManager.createOrder(c);

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals(10000, saved.getTotalAmount());
        assertEquals(3000, saved.getDiscountAmount());
        assertEquals(7000, saved.getPayAmount());
        verify(couponClient).useCoupon(eq(100L), eq(55L), anyString(), eq(10000),
                anyInt(), anyLong(), nullable(String.class), nullable(String.class));
    }

    @Test
    @DisplayName("createOrder(NORMAL) — V5.6 运费：postFee 单列快照且 payAmount = total − discount + postFee")
    void shouldIncludePostFeeInPayAmount_whenFreightTemplateApplied() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());
        when(couponClient.useCoupon(eq(100L), eq(55L), anyString(), eq(10000),
                anyInt(), anyLong(), nullable(String.class), nullable(String.class))).thenReturn(3000);
        // Given 运费模板计价结果为 800 分
        when(freightPostageResolver.resolve(eq(9L), anyList(), eq(10000))).thenReturn(800);

        OrderCreateContext c = ctx(OrderTypeEnum.NORMAL);
        c.setUserCouponId(55L);
        OrderVO vo = orderManager.createOrder(c);

        // Then totalAmount 仍 = Σ subTotal（运费不进商品小计）；payAmount 计入运费
        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals(10000, saved.getTotalAmount());
        assertEquals(3000, saved.getDiscountAmount());
        assertEquals(800, saved.getPostFee());
        assertEquals(10000 - 3000 + 800, saved.getPayAmount());
        // 出参同步
        assertEquals(800, vo.getPostFee());
        assertEquals(10000 - 3000 + 800, vo.getPayAmount());
    }

    @Test
    @DisplayName("createOrder(FLASH_SALE) — 秒杀订单运费恒为 0（规格第 6 条）")
    void shouldChargeNoPostage_whenFlashSaleOrder() {
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());

        OrderCreateContext c = ctx(OrderTypeEnum.FLASH_SALE);
        c.setSkuId(1000L);
        c.setQuantity(1);
        c.setFlashId(99L);
        c.setFlashPrice(1000);
        OrderVO vo = orderManager.createOrder(c);

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals(0, saved.getPostFee());
        assertEquals(1000, saved.getPayAmount());
        // 秒杀不查运费模板
        verify(freightPostageResolver, never()).resolve(any(), anyList(), anyInt());
    }

    // ==================== V5.7 买家留言 remark（随单快照） ====================

    @Test
    @DisplayName("createOrder(NORMAL) — V5.7 买家留言：随单快照落库且 VO 回显")
    void shouldPersistAndEchoRemark_whenCartOrderWithRemark() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());

        OrderCreateContext c = ctx(OrderTypeEnum.NORMAL);
        c.setRemark("请包装牢固，谢谢");
        OrderVO vo = orderManager.createOrder(c);

        // Then 聚合/PO（经仓储往返）携带 remark
        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals("请包装牢固，谢谢", saved.getRemark());
        // And VO 回显
        assertEquals("请包装牢固，谢谢", vo.getRemark());
    }

    @Test
    @DisplayName("createOrder(DIRECT) — V5.7 买家留言：直购单同样随单快照落库且 VO 回显")
    void shouldPersistAndEchoRemark_whenDirectOrderWithRemark() {
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());

        OrderCreateContext c = ctx(OrderTypeEnum.DIRECT);
        c.setSkuId(1000L);
        c.setQuantity(1);
        c.setRemark("放前台驿站即可");
        OrderVO vo = orderManager.createOrder(c);

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals("放前台驿站即可", saved.getRemark());
        assertEquals("放前台驿站即可", vo.getRemark());
    }

    @Test
    @DisplayName("createOrder(NORMAL) — V5.7 未传买家留言：remark 落库为 null 不报错")
    void shouldPersistNullRemark_whenRemarkAbsent() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());

        OrderCreateContext c = ctx(OrderTypeEnum.NORMAL);
        OrderVO vo = orderManager.createOrder(c);

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertNotNull(saved, "订单应正常落库");
        assertNull(saved.getRemark(), "未传留言应落库为 null");
        assertNull(vo.getRemark());
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
        OrderRepository failingRepository = mock(OrderRepository.class);
        doThrow(new RuntimeException("DB down")).when(failingRepository)
                .saveWithItems(any(), anyList());

        NormalCartOrderStrategy normalStrategy = new NormalCartOrderStrategy(
                cartClient, skuClient, addressClient,
                new PricingService(promotionClient, memberClient, couponClient), freightPostageResolver);
        OrderCreateStrategyFactory factory = new OrderCreateStrategyFactory(List.of(normalStrategy));
        OrderCreationManager mgr = newManager(failingRepository, factory);

        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());

        assertThrows(BizException.class, () -> mgr.createOrder(ctx(OrderTypeEnum.NORMAL)));
        verify(skuClient).deductStock(anyList());
        verify(skuClient).restoreStock(anyList());
    }

    @Test
    @DisplayName("createOrder — 后置处理(afterCreate)失败：补偿覆盖持久化之后的步骤，回滚库存+优惠券")
    void shouldCompensateStockAndCoupon_whenAfterCreateFails() {
        OrderCreateStrategy failingAfterCreate = new OrderCreateStrategy() {
            @Override
            public OrderTypeEnum supportedType() {
                return OrderTypeEnum.NORMAL;
            }

            @Override
            public void validate(OrderCreateContext ctx) {
                OrderCreateContext.CartItemSnapshot snap = new OrderCreateContext.CartItemSnapshot();
                snap.setSkuId(1000L);
                snap.setSellerId(9L);
                snap.setProductName("商品1000");
                snap.setPrice(5000);
                snap.setQuantity(2);
                ctx.setSnapshots(List.of(snap));
                ctx.setSellerId(9L);
                ctx.setAddress(address());
            }

            @Override
            public Order buildOrder(OrderCreateContext ctx) {
                int total = 10000;
                Order o = new Order();
                o.setOrderNo(cn.hutool.core.util.IdUtil.fastSimpleUUID());
                o.setUserId(ctx.getUserId());
                o.setSellerId(9L);
                o.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());
                o.setOrderType(OrderTypeEnum.NORMAL.getCode());
                o.setTotalAmount(total);
                o.setDiscountAmount(1000);
                o.setPayAmount(total - 1000);
                o.setReceiverName("张三");
                o.setReceiverPhone("13800138000");
                o.setReceiverAddress("广东省深圳市南山区科技路1号");
                ctx.setTotalAmount(total);
                ctx.setDiscountAmount(1000);
                return o;
            }

            @Override
            public void afterCreate(OrderCreateContext ctx, Order order) {
                throw new IllegalStateException("cart cleanup boom");
            }
        };
        OrderCreateStrategyFactory factory = new OrderCreateStrategyFactory(List.of(failingAfterCreate));
        OrderCreationManager mgr = newManager(orderRepository, factory);

        assertThrows(BizException.class, () -> mgr.createOrder(ctx(OrderTypeEnum.NORMAL)));
        verify(skuClient).deductStock(anyList());
        verify(skuClient).restoreStock(anyList());
        verify(couponClient).rollbackByOrderNo(anyString());
    }

    @Test
    @DisplayName("createOrder — 扣库存失败：抛 STOCK_INSUFFICIENT 且不落库、不补偿")
    void shouldRejectWithoutPersist_whenDeductStockFails() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());
        doThrow(new RuntimeException("sku service down")).when(skuClient).deductStock(anyList());

        BizException ex = assertThrows(BizException.class,
                () -> orderManager.createOrder(ctx(OrderTypeEnum.NORMAL)));
        assertEquals(org.icedamericanomall.common.enums.ErrorCode.STOCK_INSUFFICIENT, ex.getErrorCode());
        verify(skuClient, never()).restoreStock(anyList());
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
        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals(1000, saved.getTotalAmount()); // 秒杀价
        assertEquals(1000, saved.getPayAmount());   // 秒杀不使用优惠券，应付=原价
        assertEquals(0, saved.getDiscountAmount()); // 秒杀不使用优惠券
        verify(cartClient, never()).deleteByIds(anyLong(), anyList());
    }

    // ==================== F5 价格计算中心（ADR-043）：促销/会员/券三源叠加 ====================

    private PromotionCO promotion(int type, int threshold, int discountValue, int maxDiscount) {
        return new PromotionCO(1L, "店铺促销", 9L, type, threshold, discountValue, maxDiscount, null);
    }

    @Test
    @DisplayName("createOrder(NORMAL) — 带促销下单：I44 满减活动生效，discountAmount 命中且 payAmount 扣减")
    void shouldApplyPromotionDiscount_whenActivePromotionExists() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());
        // Given 店铺满 10000 减 2000 生效
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenReturn(List.of(promotion(1, 10000, 2000, 0)));

        OrderCreateContext c = ctx(OrderTypeEnum.NORMAL);
        OrderVO vo = orderManager.createOrder(c);

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals(10000, saved.getTotalAmount());
        assertEquals(2000, saved.getDiscountAmount());
        assertEquals(8000, saved.getPayAmount());
    }

    @Test
    @DisplayName("createOrder(NORMAL) — 促销+会员+券三源叠加封顶：合计超 total → discountAmount = total")
    void shouldCapDiscount_whenThreeSourcesExceedTotal() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenReturn(List.of(promotion(1, 0, 8000, 0)));
        // I45 fallback 降级路径模拟：GOLD 98折 + 券 9500 → 合计 17700 > total 10000
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("GOLD", 9800, 120));
        when(couponClient.useCoupon(anyLong(), anyLong(), anyString(), anyInt(), anyInt(),
                anyLong(), any(), any())).thenReturn(9500);

        OrderCreateContext c = ctx(OrderTypeEnum.NORMAL);
        c.setUserCouponId(55L);
        OrderVO vo = orderManager.createOrder(c);

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals(10000, saved.getDiscountAmount(), "总额守卫封顶为 totalAmount");
        assertEquals(0, saved.getPayAmount(), "payAmount 恒 ≥ 0");
    }

    @Test
    @DisplayName("createOrder(NORMAL) — 无生效促销（空集合合法业务态）：仅券抵扣正常下单")
    void shouldOrderNormally_whenNoActivePromotions() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());
        when(promotionClient.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of());
        when(couponClient.useCoupon(anyLong(), anyLong(), anyString(), anyInt(), anyInt(),
                anyLong(), any(), any())).thenReturn(3000);

        OrderCreateContext c = ctx(OrderTypeEnum.NORMAL);
        c.setUserCouponId(55L);
        OrderVO vo = orderManager.createOrder(c);

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals(3000, saved.getDiscountAmount());
        assertEquals(7000, saved.getPayAmount());
    }

    @Test
    @DisplayName("createOrder(NORMAL) — I44 促销查询失败（fail-closed）：下单失败透传，券未核销、库存未扣")
    void shouldFailOrder_whenPromotionQueryFails() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenThrow(new BizException(org.icedamericanomall.common.enums.ErrorCode.SYSTEM_BUSY,
                        "促销计价服务暂不可用"));

        assertThrows(BizException.class, () -> orderManager.createOrder(ctx(OrderTypeEnum.NORMAL)));

        verify(couponClient, never()).useCoupon(anyLong(), anyLong(), anyString(),
                anyInt(), anyInt(), anyLong(), any(), any());
        verify(skuClient, never()).deductStock(anyList());
    }

    @Test
    @DisplayName("createOrder(NORMAL) — I45 降级默认 NORMAL：会员不折扣，仅促销/券生效")
    void shouldNotApplyMemberDiscount_whenMemberLevelFallsBackToNormal() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenReturn(List.of(promotion(1, 0, 1000, 0)));
        // fallback 降级默认 NORMAL（10000=不折扣）
        when(memberClient.getLevel(100L)).thenReturn(new MemberLevelCO("NORMAL", 10000, 100));

        OrderCreateContext c = ctx(OrderTypeEnum.NORMAL);
        OrderVO vo = orderManager.createOrder(c);

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals(1000, saved.getDiscountAmount(), "仅促销减免，无会员折扣");
        assertEquals(9000, saved.getPayAmount());
    }

    @Test
    @DisplayName("createOrder(FLASH_SALE) — 秒杀单不进计价中心：不查促销/会员，discountAmount 恒 0")
    void shouldNotEnterPricingCenter_whenFlashSaleOrder() {
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());

        OrderCreateContext c = ctx(OrderTypeEnum.FLASH_SALE);
        c.setSkuId(1000L);
        c.setQuantity(1);
        c.setFlashId(99L);
        c.setFlashPrice(1000);
        orderManager.createOrder(c);

        verify(promotionClient, never()).queryActive(any(PromotionQueryCO.class));
        verify(memberClient, never()).getLevel(anyLong());
        verify(couponClient, never()).useCoupon(anyLong(), anyLong(), anyString(),
                anyInt(), anyInt(), anyLong(), any(), any());
    }

    // ==================== Sentinel blockHandler（流控 / 熔断统一出口） ====================

    @Test
    @DisplayName("blockHandler — 流控命中：抛 FREQUENT_ERROR（HTTP 429），不进下单管道")
    void shouldThrowFrequentError_whenFlowBlocked() {
        OrderCreateContext c = ctx(OrderTypeEnum.NORMAL);

        BizException ex = assertThrows(BizException.class, () -> orderManager.createOrderBlockHandler(
                c, new com.alibaba.csp.sentinel.slots.block.flow.FlowException("createOrder")));

        assertEquals(org.icedamericanomall.common.enums.ErrorCode.FREQUENT_ERROR, ex.getErrorCode());
        // 被限流即拒绝，不应触发任何 Feign（未进入任何下单步骤）
        verify(skuClient, never()).deductStock(anyList());
        verify(cartClient, never()).getSelectedItems(anyLong());
    }

    @Test
    @DisplayName("blockHandler — 熔断打开：同样抛 FREQUENT_ERROR")
    void shouldThrowFrequentError_whenDegradeBlocked() {
        OrderCreateContext c = ctx(OrderTypeEnum.NORMAL);

        BizException ex = assertThrows(BizException.class, () -> orderManager.createOrderBlockHandler(
                c, new com.alibaba.csp.sentinel.slots.block.degrade.DegradeException("createOrder")));

        assertEquals(org.icedamericanomall.common.enums.ErrorCode.FREQUENT_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("createOrder — 注解接线：资源名 createOrder 且 blockHandler 为同类方法")
    void shouldAnnotateCreateOrderWithSentinelResource() throws Exception {
        var method = OrderCreationManager.class.getMethod("createOrder", OrderCreateContext.class);
        var annotation = method.getAnnotation(
                com.alibaba.csp.sentinel.annotation.SentinelResource.class);

        assertNotNull(annotation, "createOrder 必须标注 @SentinelResource（SentinelConfig 规则以资源名匹配）");
        assertEquals("createOrder", annotation.value());
        assertEquals("createOrderBlockHandler", annotation.blockHandler());
        OrderCreationManager.class.getMethod("createOrderBlockHandler",
                OrderCreateContext.class, com.alibaba.csp.sentinel.slots.block.BlockException.class);
    }
}
