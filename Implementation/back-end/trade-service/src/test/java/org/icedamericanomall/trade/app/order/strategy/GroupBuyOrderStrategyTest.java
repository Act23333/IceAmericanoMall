package org.icedamericanomall.trade.app.order.strategy;

import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.client.GroupBuyClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.AddressCO;
import org.icedamericanomall.dto.GroupBuyValidateCO;
import org.icedamericanomall.dto.GroupBuyValidateResultCO;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.trade.app.order.GroupBuyJoinContextStore;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GroupBuyOrderStrategy 拼团下单策略单元测试（F13, T27/ADR-049）：
 * 拼团价快照 / quantity 恒 1 / discountAmount=0 不进定价中心 / I52 fail-closed / 运费常规计价 /
 * 参团上下文写入。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GroupBuyOrderStrategy 拼团下单策略单元测试")
class GroupBuyOrderStrategyTest {

    @Mock private GroupBuyClient groupBuyClient;
    @Mock private SkuClient skuClient;
    @Mock private AddressClient addressClient;
    @Mock private FreightPostageResolver freightPostageResolver;
    @Mock private GroupBuyJoinContextStore joinContextStore;

    private GroupBuyOrderStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new GroupBuyOrderStrategy(
                groupBuyClient, skuClient, addressClient, freightPostageResolver, joinContextStore);
    }

    private GroupBuyValidateResultCO validateResult(String teamNo) {
        // 构造器顺序：activityId, teamNo, groupPrice, requiredMembers, timeLimitHours, skuId
        return new GroupBuyValidateResultCO(1L, teamNo, 1000, 3, 24, 1000L);
    }

    private SkuCO sku() {
        SkuCO s = new SkuCO();
        s.setId(1000L);
        s.setProductId(100L);
        s.setCategoryId(10L);
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

    private OrderCreateContext ctx(String teamNo) {
        OrderCreateContext c = new OrderCreateContext();
        c.setUserId(100L);
        c.setOrderType(OrderTypeEnum.GROUP_BUY);
        c.setActivityId(1L);
        c.setTeamNo(teamNo);
        c.setAddressId(1L);
        return c;
    }

    @Test
    @DisplayName("validate — I52 下发拼团价与绑定 SKU：快照价=拼团价、quantity 恒 1、卖家透传")
    void shouldSnapshotGroupPrice_whenValidateWithI52Result() {
        when(groupBuyClient.validate(any(GroupBuyValidateCO.class))).thenReturn(validateResult("T-9"));
        when(skuClient.getSkuListByIds(List.of(1000L))).thenReturn(List.of(sku()));
        when(addressClient.getAddress(1L)).thenReturn(address());

        OrderCreateContext c = ctx("T-9");
        strategy.validate(c);

        // I52 入参原样透传（activityId/teamNo/userId）
        ArgumentCaptor<GroupBuyValidateCO> captor = ArgumentCaptor.forClass(GroupBuyValidateCO.class);
        verify(groupBuyClient).validate(captor.capture());
        assertEquals(1L, captor.getValue().getActivityId());
        assertEquals("T-9", captor.getValue().getTeamNo());
        assertEquals(100L, captor.getValue().getUserId());

        assertEquals(1000, c.getGroupPrice());
        assertEquals(1, c.getSnapshots().size());
        var snap = c.getSnapshots().get(0);
        assertEquals(1000L, snap.getSkuId());
        assertEquals(1000, snap.getPrice(), "快照价必须是拼团价而非 SKU 原价");
        assertEquals(1, snap.getQuantity(), "一人一单一件，quantity 恒 1");
        assertEquals(9L, snap.getSellerId());
        assertEquals(9L, c.getSellerId());
        assertEquals(7L, snap.getFreightTemplateId());
        assertEquals("张三", c.getAddress().getReceiver());
    }

    @Test
    @DisplayName("validate — I52 降级/失败（fail-closed）：异常上抛，不查 SKU 不查地址")
    void shouldPropagate_whenValidateFails() {
        when(groupBuyClient.validate(any(GroupBuyValidateCO.class)))
                .thenThrow(new BizException(ErrorCode.SYSTEM_BUSY, "拼团服务暂不可用，请稍后重试"));

        assertThrows(BizException.class, () -> strategy.validate(ctx(null)));

        verify(skuClient, never()).getSkuListByIds(anyList());
        verify(addressClient, never()).getAddress(anyLong());
    }

    @Test
    @DisplayName("validate — I52 结果缺拼团价/绑定 SKU：拒绝下单（结果异常防御）")
    void shouldReject_whenValidateResultIncomplete() {
        when(groupBuyClient.validate(any(GroupBuyValidateCO.class)))
                .thenReturn(new GroupBuyValidateResultCO(1L, null, null, 3, 24, null));

        assertThrows(BizException.class, () -> strategy.validate(ctx(null)));
    }

    @Test
    @DisplayName("validate — SKU 不存在：拒绝下单")
    void shouldReject_whenSkuMissing() {
        when(groupBuyClient.validate(any(GroupBuyValidateCO.class))).thenReturn(validateResult(null));
        when(skuClient.getSkuListByIds(List.of(1000L))).thenReturn(List.of());

        assertThrows(BizException.class, () -> strategy.validate(ctx(null)));
        verify(addressClient, never()).getAddress(anyLong());
    }

    @Test
    @DisplayName("validate — 限量 SKU 库存不足：拒绝下单")
    void shouldReject_whenStockInsufficient() {
        when(groupBuyClient.validate(any(GroupBuyValidateCO.class))).thenReturn(validateResult(null));
        SkuCO outOfStock = sku();
        outOfStock.setStock(0);
        when(skuClient.getSkuListByIds(List.of(1000L))).thenReturn(List.of(outOfStock));

        BizException ex = assertThrows(BizException.class, () -> strategy.validate(ctx(null)));
        assertEquals(ErrorCode.STOCK_INSUFFICIENT, ex.getErrorCode());
    }

    @Test
    @DisplayName("validate — 不限量 SKU（stockType=2）不校验库存上限")
    void shouldSkipStockCheck_whenUnlimitedStock() {
        when(groupBuyClient.validate(any(GroupBuyValidateCO.class))).thenReturn(validateResult(null));
        SkuCO unlimited = sku();
        unlimited.setStockType(2);
        unlimited.setStock(0);
        when(skuClient.getSkuListByIds(List.of(1000L))).thenReturn(List.of(unlimited));
        when(addressClient.getAddress(1L)).thenReturn(address());

        assertDoesNotThrow(() -> strategy.validate(ctx(null)));
    }

    @Test
    @DisplayName("buildOrder — discountAmount=0 不进定价中心；payAmount = 拼团价 − 0 + 常规运费")
    void shouldChargeRegularPostage_whenBuildOrder() {
        when(groupBuyClient.validate(any(GroupBuyValidateCO.class))).thenReturn(validateResult(null));
        when(skuClient.getSkuListByIds(List.of(1000L))).thenReturn(List.of(sku()));
        when(addressClient.getAddress(1L)).thenReturn(address());

        OrderCreateContext c = ctx(null);
        strategy.validate(c);
        when(freightPostageResolver.resolve(eq(9L), anyList(), eq(1000))).thenReturn(800);

        Order order = strategy.buildOrder(c);

        assertEquals(OrderTypeEnum.GROUP_BUY.getCode(), order.getOrderType());
        assertEquals(1000, order.getTotalAmount(), "totalAmount = 拼团价 × 1");
        assertEquals(0, order.getDiscountAmount(), "拼团价不叠加券/促销/会员（ADR-043）");
        assertEquals(800, order.getPostFee(), "运费走常规计价");
        assertEquals(1000 + 800, order.getPayAmount(), "payAmount = total − discount + postFee");
        assertEquals("张三", order.getReceiverName());
        verify(freightPostageResolver).resolve(eq(9L), anyList(), eq(1000));
    }

    @Test
    @DisplayName("afterCreate — 参团上下文（activityId/teamNo）写入 Redis 供支付成功参团消费")
    void shouldSaveJoinContext_whenAfterCreate() {
        Order order = new Order();
        order.setOrderNo("GB-ORD-1");

        strategy.afterCreate(ctx("T-9"), order);

        verify(joinContextStore).save("GB-ORD-1", 1L, "T-9");
    }
}
