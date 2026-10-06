package org.icedamericanomall.trade.app.order;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.CurrencyClient;
import org.icedamericanomall.client.GroupBuyClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.AddressCO;
import org.icedamericanomall.dto.GroupBuyValidateCO;
import org.icedamericanomall.dto.GroupBuyValidateResultCO;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.dto.StockOpCO;
import org.icedamericanomall.trade.adapter.assembler.OrderConverterImpl;
import org.icedamericanomall.trade.adapter.request.GroupBuyOrderReq;
import org.icedamericanomall.trade.adapter.response.OrderVO;
import org.icedamericanomall.trade.app.order.strategy.GroupBuyOrderStrategy;
import org.icedamericanomall.trade.app.order.strategy.FreightPostageResolver;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateStrategyFactory;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
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
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderCreationManager T27 拼团下单集成测试（F13, ADR-049 两段式参团第一步）。
 * 真实策略 + 真实仓储实现（H2）+ Mock Feign/Redis 存取，验证统一创建管道与拼团价口径。
 */
@DisplayName("OrderCreationManager T27 拼团下单集成测试")
class OrderCreationManagerGroupBuyTest {

    private OrderRepository orderRepository;
    private GroupBuyClient groupBuyClient;
    private SkuClient skuClient;
    private AddressClient addressClient;
    private FreightPostageResolver freightPostageResolver;
    private GroupBuyJoinContextStore joinContextStore;
    private OrderCreationManager orderManager;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:groupbuy_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE orders (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    order_no VARCHAR(64) NOT NULL UNIQUE,
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

        this.groupBuyClient = mock(GroupBuyClient.class);
        this.skuClient = mock(SkuClient.class);
        this.addressClient = mock(AddressClient.class);
        this.freightPostageResolver = mock(FreightPostageResolver.class);
        this.joinContextStore = mock(GroupBuyJoinContextStore.class);

        GroupBuyOrderStrategy strategy = new GroupBuyOrderStrategy(
                groupBuyClient, skuClient, addressClient, freightPostageResolver, joinContextStore);
        OrderCreateStrategyFactory factory = new OrderCreateStrategyFactory(List.of(strategy));

        this.orderManager = new OrderCreationManager(orderRepository, new OrderConverterImpl(),
                skuClient, mock(CouponClient.class),
                mock(OrderTimeoutPublisher.class), factory, mock(DomainEventProducer.class),
                new OrderDisplaySnapshotService(mock(CurrencyClient.class)));
    }

    private GroupBuyValidateResultCO validateResult(String teamNo) {
        return new GroupBuyValidateResultCO(1L, teamNo, 1000, 3, 24, 1000L);
    }

    private SkuCO sku() {
        SkuCO s = new SkuCO();
        s.setId(1000L);
        s.setProductId(100L);
        s.setSellerId(9L);
        s.setProductName("拼团商品");
        s.setSpec("500ml");
        s.setPrice(5000);
        s.setStock(10);
        s.setImage("img.png");
        s.setFreightTemplateId(7L);
        return s;
    }

    private AddressCO address() {
        AddressCO a = new AddressCO();
        a.setId(1L);
        a.setReceiver("张三");
        a.setPhone("13800138000");
        a.setProvince("广东省");
        a.setCity("深圳市");
        a.setDistrict("南山区");
        a.setStreet("科技路");
        a.setDetail("1号");
        return a;
    }

    private GroupBuyOrderReq req(Long activityId, String teamNo) {
        GroupBuyOrderReq r = new GroupBuyOrderReq();
        r.setActivityId(activityId);
        r.setTeamNo(teamNo);
        r.setAddressId(1L);
        r.setRemark("拼团备注");
        return r;
    }

    private void stubHappyPath(String teamNo) {
        when(groupBuyClient.validate(any(GroupBuyValidateCO.class))).thenReturn(validateResult(teamNo));
        when(skuClient.getSkuListByIds(List.of(1000L))).thenReturn(List.of(sku()));
        when(addressClient.getAddress(1L)).thenReturn(address());
        when(freightPostageResolver.resolve(eq(9L), anyList(), eq(1000))).thenReturn(500);
    }

    @Test
    @DisplayName("createGroupBuyOrder — 开团下单：拼团价快照 + 运费常规件 + 上下文写入 + 扣库存")
    void shouldCreateGroupBuyOrder_whenValidOpenTeam() {
        stubHappyPath(null);

        OrderVO vo = orderManager.createGroupBuyOrder(100L, req(1L, null));

        assertNotNull(vo.getOrderNo());
        assertEquals(OrderTypeEnum.GROUP_BUY.getCode(), vo.getOrderType());
        Order saved = orderRepository.findByOrderNo(vo.getOrderNo());
        assertEquals(1000, saved.getTotalAmount(), "totalAmount = 拼团价 × 1");
        assertEquals(0, saved.getDiscountAmount(), "不叠加券/促销/会员");
        assertEquals(500, saved.getPostFee());
        assertEquals(1500, saved.getPayAmount());
        assertEquals(OrderStatusEnum.PENDING_PAYMENT.getCode(), saved.getStatus());
        assertEquals("拼团备注", saved.getRemark());
        assertEquals(9L, saved.getSellerId());
        assertEquals(1000, vo.getItems().get(0).getPrice());
        assertEquals(1, vo.getItems().get(0).getQuantity());
        assertEquals(1000, vo.getItems().get(0).getSubTotal());

        // 扣库存使用 I52 下发的活动绑定 SKU，数量恒 1
        ArgumentCaptor<List<StockOpCO>> stockCaptor = ArgumentCaptor.forClass(List.class);
        verify(skuClient).deductStock(stockCaptor.capture());
        assertEquals(1, stockCaptor.getValue().size());
        assertEquals(1000L, stockCaptor.getValue().get(0).getSkuId());
        assertEquals(1, stockCaptor.getValue().get(0).getQuantity());

        // 参团上下文随单写入（orderNo → activityId/teamNo）
        verify(joinContextStore).save(vo.getOrderNo(), 1L, null);
    }

    @Test
    @DisplayName("createGroupBuyOrder — 参团下单：teamNo 透传 I52 并随上下文写入")
    void shouldPassTeamNoThrough_whenJoinExistingTeam() {
        stubHappyPath("T-9");

        OrderVO vo = orderManager.createGroupBuyOrder(100L, req(1L, "T-9"));

        ArgumentCaptor<GroupBuyValidateCO> captor = ArgumentCaptor.forClass(GroupBuyValidateCO.class);
        verify(groupBuyClient).validate(captor.capture());
        assertEquals(1L, captor.getValue().getActivityId());
        assertEquals("T-9", captor.getValue().getTeamNo());
        assertEquals(100L, captor.getValue().getUserId());
        verify(joinContextStore).save(vo.getOrderNo(), 1L, "T-9");
    }

    @Test
    @DisplayName("createGroupBuyOrder — I52 fail-closed：下单失败不落库不扣库存不写上下文")
    void shouldFailWithoutPersist_whenValidateFails() {
        when(groupBuyClient.validate(any(GroupBuyValidateCO.class)))
                .thenThrow(new BizException(ErrorCode.SYSTEM_BUSY, "拼团服务暂不可用，请稍后重试"));

        assertThrows(BizException.class, () -> orderManager.createGroupBuyOrder(100L, req(1L, null)));

        assertEquals(0, orderRepository.countAll(), "不得落库");
        verify(skuClient, never()).deductStock(anyList());
        verify(joinContextStore, never()).save(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("createGroupBuyOrder — 地址不存在：拒绝下单且不扣库存")
    void shouldReject_whenAddressMissing() {
        when(groupBuyClient.validate(any(GroupBuyValidateCO.class))).thenReturn(validateResult(null));
        when(skuClient.getSkuListByIds(List.of(1000L))).thenReturn(List.of(sku()));
        when(addressClient.getAddress(1L)).thenReturn(null);

        assertThrows(BizException.class, () -> orderManager.createGroupBuyOrder(100L, req(1L, null)));

        assertEquals(0, orderRepository.countAll());
        verify(skuClient, never()).deductStock(anyList());
    }

    @Test
    @DisplayName("createGroupBuyOrder — 参团上下文写入失败：下单失败 + Saga 回滚已扣库存（付款前闭环）")
    void shouldRestoreStock_whenContextSaveFails() {
        stubHappyPath(null);
        doThrow(new IllegalStateException("redis down")).when(joinContextStore)
                .save(anyString(), anyLong(), any());

        assertThrows(BizException.class, () -> orderManager.createGroupBuyOrder(100L, req(1L, null)));

        verify(skuClient).deductStock(anyList());
        verify(skuClient).restoreStock(anyList());
    }
}
