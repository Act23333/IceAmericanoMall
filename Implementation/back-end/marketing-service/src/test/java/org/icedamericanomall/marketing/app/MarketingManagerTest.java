package org.icedamericanomall.marketing.app;

import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CreateOrderInternalReq;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverterImpl;
import org.icedamericanomall.marketing.adapter.response.FlashBuyVO;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
import org.icedamericanomall.marketing.app.dto.FlashBuyResult;
import org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief;
import org.icedamericanomall.marketing.app.flash.FlashSaleManager;
import org.icedamericanomall.marketing.app.flash.FlashSaleViewManager;
import org.icedamericanomall.marketing.app.port.FlashSaleSpuBriefQuery;
import org.icedamericanomall.marketing.domain.dto.FlashSaleOrderMessage;
import org.icedamericanomall.marketing.domain.model.flash.FlashSale;
import org.icedamericanomall.marketing.infrastructure.messaging.FlashSaleOrderPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * MarketingManager 秒杀编排单元测试 —— 预扣 → Feign 落单 → MQ 统计。
 *
 * <p>验证编排契约：{@code /api/flash/buy} 必须走 Manager（Feign/MQ 只在应用层），
 * orderNo 由 trade-service 返回并回填，MQ 失败不影响主流程。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MarketingManager 秒杀编排单元测试")
class MarketingManagerTest {

    private static final Long USER_ID = 7L;
    private static final Long FLASH_ID = 1L;
    private static final Long ADDRESS_ID = 33L;
    private static final Long SKU_ID = 1000L;
    /** 出线格式的 SPU/SKU 业务键（32-hex；与技术键刻意不同值）。 */
    private static final String SKU_BUSINESS_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String SPU_BUSINESS_ID = "aabbccddeeff00112233445566778899";
    private static final Long SPU_ID = 100L;
    private static final int FLASH_PRICE = 9900;
    private static final String ORDER_NO = "ORD-FLASH-001";

    @Mock FlashSaleManager flashSaleManager;
    @Mock FlashSaleSpuBriefQuery flashSaleSpuBriefQuery;
    @Mock OrderClient orderClient;
    @Mock FlashSaleOrderPublisher publisher;

    private MarketingManager marketingManager;

    @BeforeEach
    void setUp() {
        marketingManager = new MarketingManager(flashSaleManager,
                new FlashSaleViewManager(new MarketingConverterImpl(), flashSaleSpuBriefQuery),
                orderClient, publisher, new MarketingConverterImpl());
        // 读模型端口：技术键（出参回填）与业务键（下单链路解析）两种口径都命中同一行（ADR-061）——
        // 故秒杀下单链路才能把场次绑定的 SKU 技术键换成业务键后调 I14
        lenient().when(flashSaleSpuBriefQuery.findBySkuIds(any()))
                .thenReturn(java.util.Map.of(SKU_ID, brief()));
        lenient().when(flashSaleSpuBriefQuery.findBySkuBusinessIds(any()))
                .thenReturn(java.util.Map.of(SKU_BUSINESS_ID, brief()));
        UserContext.setUser(UserInfo.builder().userId(USER_ID).build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("buyFlashSale — 预扣成功+落单成功 → 回填 orderNo 并按约定参数下单")
    void shouldBackfillOrderNoAndCreateOrder_whenDeductSucceeds() {
        when(flashSaleManager.buy(FLASH_ID, ADDRESS_ID)).thenReturn(preDeducted());
        when(orderClient.createOrder(any())).thenReturn(orderSummary(ORDER_NO));

        FlashBuyVO result = marketingManager.buyFlashSale(FLASH_ID, ADDRESS_ID);

        assertEquals(ORDER_NO, result.getOrderNo());
        assertEquals(9L, result.getRemainingStock().longValue());

        ArgumentCaptor<CreateOrderInternalReq> captor = ArgumentCaptor.forClass(CreateOrderInternalReq.class);
        verify(orderClient).createOrder(captor.capture());
        CreateOrderInternalReq req = captor.getValue();
        assertEquals(USER_ID, req.getUserId());
        assertEquals(SKU_BUSINESS_ID, req.getSkuId(),
                "ADR-061 §决策 2：I14 下单入参是 SKU 业务键（技术键 1000 不得出现）");
        assertEquals(0L, req.getSellerId().longValue(), "秒杀商品无店铺归属口径，sellerId 固定 0");
        assertEquals(1, req.getQuantity().intValue());
        assertEquals(FLASH_PRICE, req.getFlashPrice().intValue());
        assertEquals(FLASH_ID, req.getFlashId());
        assertEquals(ADDRESS_ID, req.getAddressId());
    }

    @Test
    @DisplayName("buyFlashSale — 落单成功 → 投递 MQ 异步统计消息")
    void shouldPublishMq_whenOrderCreated() {
        when(flashSaleManager.buy(FLASH_ID, ADDRESS_ID)).thenReturn(preDeducted());
        when(orderClient.createOrder(any())).thenReturn(orderSummary(ORDER_NO));

        marketingManager.buyFlashSale(FLASH_ID, ADDRESS_ID);

        ArgumentCaptor<FlashSaleOrderMessage> captor = ArgumentCaptor.forClass(FlashSaleOrderMessage.class);
        verify(publisher).publish(captor.capture());
        FlashSaleOrderMessage msg = captor.getValue();
        assertEquals(ORDER_NO, msg.getOrderNo());
        assertEquals(FLASH_ID, msg.getFlashId());
        assertEquals(USER_ID, msg.getUserId());
        assertEquals(SKU_ID, msg.getSkuId());
        assertEquals(1, msg.getQuantity().intValue());
    }

    @Test
    @DisplayName("buyFlashSale — Redis 售罄 → 抛已售罄且不落单、不发消息")
    void shouldThrowSoldOut_whenRedisSoldOut() {
        when(flashSaleManager.buy(FLASH_ID, ADDRESS_ID))
                .thenThrow(new BizException(ErrorCode.FLASH_SALE_SOLD_OUT));

        BizException ex = assertThrows(BizException.class,
                () -> marketingManager.buyFlashSale(FLASH_ID, ADDRESS_ID));

        assertEquals(ErrorCode.FLASH_SALE_SOLD_OUT, ex.getErrorCode());
        verifyNoInteractions(orderClient, publisher);
    }

    @Test
    @DisplayName("buyFlashSale — 超限购 → 抛限购异常且不落单、不发消息")
    void shouldThrowLimitExceeded_whenUserReachedLimit() {
        when(flashSaleManager.buy(FLASH_ID, ADDRESS_ID))
                .thenThrow(new BizException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED));

        BizException ex = assertThrows(BizException.class,
                () -> marketingManager.buyFlashSale(FLASH_ID, ADDRESS_ID));

        assertEquals(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED, ex.getErrorCode());
        verifyNoInteractions(orderClient, publisher);
    }

    @Test
    @DisplayName("buyFlashSale — trade-service 调用异常 → 收敛为 FLASH_SALE_FAILED，不发消息")
    void shouldThrowFlashSaleFailed_whenTradeServiceThrows() {
        when(flashSaleManager.buy(FLASH_ID, ADDRESS_ID)).thenReturn(preDeducted());
        when(orderClient.createOrder(any())).thenThrow(new IllegalStateException("feign timeout"));

        BizException ex = assertThrows(BizException.class,
                () -> marketingManager.buyFlashSale(FLASH_ID, ADDRESS_ID));

        assertEquals(ErrorCode.FLASH_SALE_FAILED, ex.getErrorCode());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("buyFlashSale — trade-service 返回空 → 抛 FLASH_SALE_FAILED")
    void shouldThrowFlashSaleFailed_whenTradeServiceReturnsNull() {
        when(flashSaleManager.buy(FLASH_ID, ADDRESS_ID)).thenReturn(preDeducted());
        when(orderClient.createOrder(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> marketingManager.buyFlashSale(FLASH_ID, ADDRESS_ID));

        assertEquals(ErrorCode.FLASH_SALE_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("buyFlashSale — MQ 发布失败 → 主流程仍返回 orderNo（非关键路径）")
    void shouldStillReturnOrderNo_whenMqPublishFails() {
        when(flashSaleManager.buy(FLASH_ID, ADDRESS_ID)).thenReturn(preDeducted());
        when(orderClient.createOrder(any())).thenReturn(orderSummary(ORDER_NO));
        when(publisher.publish(any())).thenThrow(new IllegalStateException("mq down"));

        FlashBuyVO result = marketingManager.buyFlashSale(FLASH_ID, ADDRESS_ID);

        assertEquals(ORDER_NO, result.getOrderNo());
        assertTrue(result.getSuccess());
    }

    @Test
    @DisplayName("listActiveFlashSales — 透传进行中场次并回填商品名/主图/原价/进度百分比")
    void shouldPassThroughActiveFlashSales_whenList() {
        FlashSale flashSale = FlashSale.builder()
                .id(FLASH_ID)
                .spuId(SPU_ID)
                .skuId(SKU_ID)
                .flashPrice(FLASH_PRICE)
                .stock(100)
                .soldCount(35)
                .build();
        when(flashSaleManager.listActive()).thenReturn(List.of(flashSale));

        List<FlashSaleVO> result = marketingManager.listActiveFlashSales();

        assertEquals(1, result.size());
        FlashSaleVO vo = result.getFirst();
        assertEquals(FLASH_ID, vo.getId());
        assertEquals("手工陶瓷茶杯", vo.getSpuName(), "ADR-061 §决策 5：product_name → spu_name");
        assertEquals("https://cdn.icedmall.com/product/main_100.jpg", vo.getMainImage(),
                "主图线上名统一 main_image");
        assertEquals(12800, vo.getOriginalPrice());
        assertEquals(35, vo.getProgressPercent());
    }

    /** 读模型行：技术键（内部）与业务键（出线格式）刻意不同值。 */
    private static org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief brief() {
        return new org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief(
                SKU_ID, SPU_ID, SKU_BUSINESS_ID, SPU_BUSINESS_ID,
                "手工陶瓷茶杯", "https://cdn.icedmall.com/product/main_100.jpg", 12800);
    }

    private FlashBuyResult preDeducted() {
        FlashBuyResult result = new FlashBuyResult();
        result.setSuccess(true);
        result.setFlashId(FLASH_ID);
        result.setSkuId(SKU_ID);
        result.setFlashPrice(FLASH_PRICE);
        result.setRemainingStock(9L);
        return result;
    }

    private OrderSummaryCO orderSummary(String orderNo) {
        OrderSummaryCO co = new OrderSummaryCO();
        co.setOrderNo(orderNo);
        co.setUserId(USER_ID);
        co.setPayAmount(FLASH_PRICE);
        return co;
    }
}
