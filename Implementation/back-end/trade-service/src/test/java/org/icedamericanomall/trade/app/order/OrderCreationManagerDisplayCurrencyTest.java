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
import org.icedamericanomall.client.GroupBuyClient;
import org.icedamericanomall.client.MemberClient;
import org.icedamericanomall.client.PromotionClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.dto.AddressCO;
import org.icedamericanomall.dto.CartItemCO;
import org.icedamericanomall.dto.CurrencyRateCO;
import org.icedamericanomall.dto.GroupBuyValidateCO;
import org.icedamericanomall.dto.GroupBuyValidateResultCO;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.trade.adapter.assembler.OrderConverterImpl;
import org.icedamericanomall.trade.adapter.request.GroupBuyOrderReq;
import org.icedamericanomall.trade.adapter.response.OrderVO;
import org.icedamericanomall.trade.app.order.pricing.PricingService;
import org.icedamericanomall.trade.app.order.strategy.DirectOrderStrategy;
import org.icedamericanomall.trade.app.order.strategy.FlashSaleOrderStrategy;
import org.icedamericanomall.trade.app.order.strategy.FreightPostageResolver;
import org.icedamericanomall.trade.app.order.strategy.GroupBuyOrderStrategy;
import org.icedamericanomall.trade.app.order.strategy.NormalCartOrderStrategy;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateContext;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateStrategyFactory;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OrderCreationManager F16 多币种展示快照集成测试（V5.17，契约 §4.6.1/§4.14.3b I61，
 * PRD §17.4.3 BDD「订单展示汇率快照」）。
 *
 * <p>真实策略 + 真实仓储（H2，含 {@code display_currency/display_rate} 两列）+
 * Mock Feign，覆盖 T1/T6/T27 三入口快照成功与 fail-open 全分支（未传/CNY 直通不查端点、
 * 降级哨兵 -1 / 查询异常跳过快照订单照常创建），并回归金额纪律：
 * 四金额字段恒 CNY 分、不变式 V2 逐字成立（快照列不参与）。
 */
@DisplayName("OrderCreationManager F16 展示汇率快照（fail-open）")
class OrderCreationManagerDisplayCurrencyTest {

    private static final String USD = "USD";
    /** 测试用定点汇率：USD 0.140300 → 140300（×10⁶）。 */
    private static final long USD_RATE_SCALED = 140300L;

    private OrderRepository orderRepository;
    private CartClient cartClient;
    private AddressClient addressClient;
    private SkuClient skuClient;
    private GroupBuyClient groupBuyClient;
    private GroupBuyJoinContextStore joinContextStore;
    private FreightPostageResolver freightPostageResolver;
    private CurrencyClient currencyClient;
    private OrderCreationManager orderManager;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:displaycur_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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
        this.groupBuyClient = mock(GroupBuyClient.class);
        this.freightPostageResolver = mock(FreightPostageResolver.class);
        this.joinContextStore = mock(GroupBuyJoinContextStore.class);
        this.currencyClient = mock(CurrencyClient.class);

        // 促销/会员默认空桩（mock 默认空集合/null）→ 无优惠，聚焦快照管道断言
        CouponClient couponClient = mock(CouponClient.class);
        PricingService pricingService = new PricingService(
                mock(PromotionClient.class), mock(MemberClient.class), couponClient);

        OrderCreateStrategyFactory factory = new OrderCreateStrategyFactory(List.of(
                new NormalCartOrderStrategy(cartClient, skuClient, addressClient,
                        pricingService, freightPostageResolver),
                new DirectOrderStrategy(skuClient, addressClient, pricingService, freightPostageResolver),
                new FlashSaleOrderStrategy(skuClient, addressClient),
                new GroupBuyOrderStrategy(groupBuyClient, skuClient, addressClient,
                        freightPostageResolver, joinContextStore)));

        this.orderManager = new OrderCreationManager(orderRepository, new OrderConverterImpl(),
                skuClient, couponClient, mock(OrderTimeoutPublisher.class), factory,
                mock(DomainEventProducer.class), new OrderDisplaySnapshotService(currencyClient));
    }

    // ==================== T1/T6/T27 三入口：快照成功 + VO 回显 ====================

    @Test
    @DisplayName("createOrder(NORMAL/T1) — 带 displayCurrency=USD：两列快照 140300 + VO 回显 + V2 不变式成立")
    void shouldSnapshotDisplayRate_whenCartOrderWithDisplayCurrency() {
        stubCartHappyPath();
        when(currencyClient.getCurrent(USD)).thenReturn(new CurrencyRateCO(USD, USD_RATE_SCALED, 2));

        OrderCreateContext c = normalCtx();
        c.setDisplayCurrency(USD);
        OrderVO vo = orderManager.createOrder(c);

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals(USD, saved.getDisplayCurrency());
        assertEquals(USD_RATE_SCALED, saved.getDisplayRate());
        assertEquals(USD, vo.getDisplayCurrency());
        assertEquals(USD_RATE_SCALED, vo.getDisplayRateScaled());
        // 金额纪律：四金额字段恒 CNY 分，V2 逐字成立（快照列不参与）
        assertEquals(10000, saved.getTotalAmount());
        assertEquals(0, saved.getDiscountAmount());
        assertEquals(800, saved.getPostFee());
        assertEquals(10000 - 0 + 800, saved.getPayAmount());
    }

    @Test
    @DisplayName("createOrder(DIRECT/T6) — 带 displayCurrency=USD：两列快照 + VO 回显")
    void shouldSnapshotDisplayRate_whenDirectOrderWithDisplayCurrency() {
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());
        when(currencyClient.getCurrent(USD)).thenReturn(new CurrencyRateCO(USD, USD_RATE_SCALED, 2));

        OrderCreateContext c = new OrderCreateContext();
        c.setUserId(100L);
        c.setOrderType(OrderTypeEnum.DIRECT);
        c.setAddressId(1L);
        c.setSkuId(1000L);
        c.setQuantity(1);
        c.setDisplayCurrency(USD);
        OrderVO vo = orderManager.createOrder(c);

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals(USD, saved.getDisplayCurrency());
        assertEquals(USD_RATE_SCALED, saved.getDisplayRate());
        assertEquals(USD, vo.getDisplayCurrency());
        assertEquals(USD_RATE_SCALED, vo.getDisplayRateScaled());
    }

    @Test
    @DisplayName("createGroupBuyOrder(T27) — 带 displayCurrency=USD：拼团单两列快照 + VO 回显")
    void shouldSnapshotDisplayRate_whenGroupBuyOrderWithDisplayCurrency() {
        when(groupBuyClient.validate(any(GroupBuyValidateCO.class)))
                .thenReturn(new GroupBuyValidateResultCO(1L, null, 1000, 3, 24, 1000L));
        when(skuClient.getSkuListByIds(List.of(1000L))).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());
        when(freightPostageResolver.resolve(eq(9L), anyList(), eq(1000))).thenReturn(500);
        when(currencyClient.getCurrent(USD)).thenReturn(new CurrencyRateCO(USD, USD_RATE_SCALED, 2));

        GroupBuyOrderReq req = new GroupBuyOrderReq();
        req.setActivityId(1L);
        req.setAddressId(1L);
        req.setDisplayCurrency(USD);
        OrderVO vo = orderManager.createGroupBuyOrder(100L, req);

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals(USD, saved.getDisplayCurrency());
        assertEquals(USD_RATE_SCALED, saved.getDisplayRate());
        assertEquals(USD, vo.getDisplayCurrency());
        assertEquals(USD_RATE_SCALED, vo.getDisplayRateScaled());
        // 拼团口径：totalAmount=拼团价×1，V2 = 1000 − 0 + 500
        assertEquals(1000, saved.getTotalAmount());
        assertEquals(1000 - 0 + 500, saved.getPayAmount());
    }

    // ==================== fail-open 全分支（PRD §17.7.2） ====================

    @Test
    @DisplayName("未传 displayCurrency — 两列 NULL 且不调 CurrencyClient（未传=CNY 不快照）")
    void shouldLeaveColumnsNullAndSkipFeign_whenDisplayCurrencyNotProvided() {
        stubCartHappyPath();

        OrderVO vo = orderManager.createOrder(normalCtx());

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertNull(saved.getDisplayCurrency());
        assertNull(saved.getDisplayRate());
        verifyNoInteractions(currencyClient);
    }

    @Test
    @DisplayName("displayCurrency=CNY — 主币种直通：不调 CurrencyClient 不快照（CNY 订单无参考价）")
    void shouldSkipFeignAndSnapshot_whenDisplayCurrencyIsCNY() {
        stubCartHappyPath();

        OrderCreateContext c = normalCtx();
        c.setDisplayCurrency(OrderDisplaySnapshotService.BASE_CURRENCY_CNY);
        OrderVO vo = orderManager.createOrder(c);

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertNull(saved.getDisplayCurrency());
        assertNull(saved.getDisplayRate());
        verifyNoInteractions(currencyClient);
    }

    @Test
    @DisplayName("Feign 降级哨兵 rateScaled=-1 — 跳过快照两列 NULL，订单照常创建（fail-open）")
    void shouldCreateOrderWithoutSnapshot_whenDegradedSentinelReturned() {
        stubCartHappyPath();
        when(currencyClient.getCurrent(USD)).thenReturn(new CurrencyRateCO(USD, -1L, null));

        OrderCreateContext c = normalCtx();
        c.setDisplayCurrency(USD);
        OrderVO vo = assertDoesNotThrow(() -> orderManager.createOrder(c));

        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertNull(saved.getDisplayCurrency());
        assertNull(saved.getDisplayRate());
        // 主流程零影响：金额照常（V2 = 10000 − 0 + 800），状态照常
        assertEquals(10000, saved.getTotalAmount());
        assertEquals(800, saved.getPostFee());
        assertEquals(10000 - 0 + 800, saved.getPayAmount());
    }

    @Test
    @DisplayName("CurrencyClient 抛异常 — 订单照常创建两列 NULL，主流程零影响（fail-open）")
    void shouldCreateOrder_whenCurrencyClientThrows() {
        stubCartHappyPath();
        when(currencyClient.getCurrent(USD)).thenThrow(new IllegalStateException("feign down"));

        OrderCreateContext c = normalCtx();
        c.setDisplayCurrency(USD);
        OrderVO vo = assertDoesNotThrow(() -> orderManager.createOrder(c));

        assertNotNull(vo.getOrderNo());
        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertNotNull(saved);
        assertNull(saved.getDisplayCurrency());
        assertNull(saved.getDisplayRate());
    }

    // ==================== 桩与构造辅助 ====================

    private void stubCartHappyPath() {
        when(cartClient.getSelectedItems(100L)).thenReturn(List.of(cartItem(1L, 1000L, 2)));
        when(skuClient.getSkuListByIds(anyList())).thenReturn(List.of(sku(1000L, 9L, 5000, 10)));
        when(addressClient.getAddress(1L)).thenReturn(address());
        when(freightPostageResolver.resolve(eq(9L), anyList(), eq(10000))).thenReturn(800);
    }

    private OrderCreateContext normalCtx() {
        OrderCreateContext c = new OrderCreateContext();
        c.setUserId(100L);
        c.setOrderType(OrderTypeEnum.NORMAL);
        c.setAddressId(1L);
        return c;
    }

    private CartItemCO cartItem(Long cartItemId, Long skuId, int qty) {
        CartItemCO c = new CartItemCO();
        c.setCartItemId(cartItemId);
        c.setSkuId(skuId);
        c.setQuantity(qty);
        return c;
    }

    private SkuCO sku(Long id, Long sellerId, int price, int stock) {
        SkuCO s = new SkuCO();
        s.setId(id);
        s.setSellerId(sellerId);
        s.setProductName("商品" + id);
        s.setSpec("规格");
        s.setPrice(price);
        s.setStock(stock);
        s.setImage("img.png");
        return s;
    }

    private AddressCO address() {
        AddressCO a = new AddressCO();
        a.setId(1L);
        a.setUserId(100L); // 归属校验：与 ctx.userId 一致
        a.setReceiver("张三");
        a.setPhone("13800138000");
        a.setProvince("广东省");
        a.setCity("深圳市");
        a.setDistrict("南山区");
        a.setStreet("科技路");
        a.setDetail("1号");
        return a;
    }
}
