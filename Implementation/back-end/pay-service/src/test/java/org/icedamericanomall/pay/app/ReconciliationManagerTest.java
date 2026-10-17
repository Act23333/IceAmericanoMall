package org.icedamericanomall.pay.app;

import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.integration.payment.ChannelBillItem;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.adapter.assembler.ReconciliationConverterImpl;
import org.icedamericanomall.pay.adapter.response.ReconciliationBatchVO;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationBatch;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationDetail;
import org.icedamericanomall.pay.domain.enums.ReconciliationResultEnum;
import org.icedamericanomall.pay.domain.enums.ReconciliationStatusEnum;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.icedamericanomall.pay.domain.repository.ReconciliationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * ReconciliationManager 渠道对账编排单元测试（F4 渠道对账 BDD）。
 *
 * <p>覆盖契约 §4.13.14 的四条 Scenario：四态明细、重复执行幂等、仅记录差异不自动调账、
 * 以及渠道参数（缺省=全部渠道）的行为。渠道账单下载与本地流水读取全部打桩
 * （「先删后写」的落库幂等在 {@code ReconciliationRepositoryImplTest} 中锁死）；
 * 出参装配用真实 MapStruct 转换器（{@code ReconciliationConverterImpl}），锁死 Domain → VO 口径。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReconciliationManager 渠道对账编排单元测试（F4）")
class ReconciliationManagerTest {

    private static final LocalDate BILL_DATE = LocalDate.of(2026, 10, 2);

    @Mock ReconciliationRepository reconciliationService;
    @Mock PayOrderRepository payOrderService;
    @Mock PaymentChannelRouter channelRouter;

    private ReconciliationManager reconciliationManager;

    @BeforeEach
    void setUp() {
        reconciliationManager = new ReconciliationManager(reconciliationService, payOrderService,
                channelRouter, new ReconciliationConverterImpl());
    }

    /**
     * 本地支付成功流水：对外渠道号 {@code chn-00x}，内部支付单号刻意是另一个值 {@code pay-00x}。
     *
     * <p>两者不同才能锁死「比对键 = 渠道号」：若实现按支付单号比对，账单里的 {@code chn-00x}
     * 一条都匹配不上，四态判定会整体塌成 CHANNEL_ONLY / LOCAL_ONLY。
     */
    private PayOrder localPaid(String channelTradeNo, int amount) {
        PayOrder payOrder = new PayOrder();
        payOrder.setPayOrderNo("pay-" + channelTradeNo.substring("chn-".length()));
        payOrder.setChannelTradeNo(channelTradeNo);
        payOrder.setPayChannelCode("WECHAT");
        payOrder.setAmount(amount);
        payOrder.setStatus(3);
        return payOrder;
    }

    private ReconciliationBatch savedBatch(String channel) {
        ReconciliationBatch batch = new ReconciliationBatch();
        batch.setId(1L);
        batch.setBillDate(BILL_DATE);
        batch.setChannel(channel);
        batch.setStatus(ReconciliationStatusEnum.COMPLETED.getCode());
        return batch;
    }

    /** 捕获本次落库的明细（按对外渠道号索引，即比对键）。 */
    @SuppressWarnings("unchecked")
    private Map<String, ReconciliationDetail> captureDetails(int invocationIndex) {
        ArgumentCaptor<List<ReconciliationDetail>> captor = ArgumentCaptor.forClass(List.class);
        verify(reconciliationService, times(invocationIndex)).saveBatchResult(eq(BILL_DATE), anyString(),
                anyInt(), anyInt(), captor.capture());
        return captor.getValue().stream()
                .collect(Collectors.toMap(ReconciliationDetail::getChannelTradeNo, Function.identity()));
    }

    @Test
    @DisplayName("当日账单比对 — 按对外渠道号生成四态明细（匹配 / 渠道有本地无 / 本地有渠道无 / 金额不一致）")
    void shouldProduceFourStates_whenComparingBill() {
        when(channelRouter.downloadWechatBill("2026-10-02")).thenReturn(List.of(
                new ChannelBillItem("chn-001", "TXN-1", 9900),
                new ChannelBillItem("chn-002", "TXN-2", 12800),
                new ChannelBillItem("chn-00X", "TXN-X", 6600),
                new ChannelBillItem("chn-003", "TXN-3", 5500)));
        when(payOrderService.listPaidByDateAndChannel(BILL_DATE, "WECHAT")).thenReturn(List.of(
                localPaid("chn-001", 9900),
                localPaid("chn-002", 12800),
                localPaid("chn-003", 5000),
                localPaid("chn-004", 3000)));
        when(reconciliationService.saveBatchResult(eq(BILL_DATE), eq("WECHAT"), anyInt(), anyInt(), anyList()))
                .thenReturn(savedBatch("WECHAT"));

        List<ReconciliationBatchVO> batches = reconciliationManager.run(BILL_DATE, "WECHAT");

        assertEquals(1, batches.size());
        Map<String, ReconciliationDetail> details = captureDetails(1);
        assertEquals(5, details.size(), "4 条渠道流水 + 1 条本地独有");
        assertEquals(ReconciliationResultEnum.MATCHED.getCode(), details.get("chn-001").getResult());
        assertEquals(ReconciliationResultEnum.MATCHED.getCode(), details.get("chn-002").getResult());
        assertEquals(ReconciliationResultEnum.CHANNEL_ONLY.getCode(), details.get("chn-00X").getResult());
        assertEquals(ReconciliationResultEnum.AMOUNT_MISMATCH.getCode(), details.get("chn-003").getResult());
        assertEquals(ReconciliationResultEnum.LOCAL_ONLY.getCode(), details.get("chn-004").getResult());
        // 金额不一致明细需同时回填两侧金额，供管理端核对
        assertEquals(5500, details.get("chn-003").getChannelAmount());
        assertEquals(5000, details.get("chn-003").getLocalAmount());
        // 渠道有本地无：localAmount 为 null 且没有本地支付单号；本地有渠道无：channelAmount 为 null
        assertEquals(null, details.get("chn-00X").getLocalAmount());
        assertEquals(null, details.get("chn-00X").getPayOrderNo());
        assertEquals(null, details.get("chn-004").getChannelAmount());
        // 总额：渠道 9900+12800+6600+5500；本地 9900+12800+5000+3000
        verify(reconciliationService).saveBatchResult(eq(BILL_DATE), eq("WECHAT"), eq(34800), eq(30700), anyList());
    }

    @Test
    @DisplayName("重复执行同日对账 — 幂等重入（每次均走「先删后写」入口，结果一致）")
    void shouldBeIdempotent_whenRunTwice() {
        when(channelRouter.downloadWechatBill("2026-10-02")).thenReturn(List.of(
                new ChannelBillItem("chn-001", "TXN-1", 9900)));
        when(payOrderService.listPaidByDateAndChannel(BILL_DATE, "WECHAT")).thenReturn(List.of(
                localPaid("chn-001", 9900)));
        when(reconciliationService.saveBatchResult(eq(BILL_DATE), eq("WECHAT"), anyInt(), anyInt(), anyList()))
                .thenReturn(savedBatch("WECHAT"));

        reconciliationManager.run(BILL_DATE, "WECHAT");
        reconciliationManager.run(BILL_DATE, "WECHAT");

        verify(reconciliationService, times(2)).saveBatchResult(eq(BILL_DATE), eq("WECHAT"), anyInt(), anyInt(),
                anyList());
        assertEquals(1, captureDetails(2).size(), "两次执行产出的明细集合一致（uk_date_channel 兜底单批次）");
    }

    @Test
    @DisplayName("仅记录差异不自动调账 — 对账只读本地流水，不触碰任何本地支付单写操作")
    void shouldNotAdjustAnything_whenDiffersFound() {
        when(channelRouter.downloadWechatBill("2026-10-02")).thenReturn(List.of(
                new ChannelBillItem("chn-003", "TXN-3", 5500)));
        when(payOrderService.listPaidByDateAndChannel(BILL_DATE, "WECHAT")).thenReturn(List.of(
                localPaid("chn-003", 5000)));
        when(reconciliationService.saveBatchResult(eq(BILL_DATE), eq("WECHAT"), anyInt(), anyInt(), anyList()))
                .thenReturn(savedBatch("WECHAT"));

        reconciliationManager.run(BILL_DATE, "WECHAT");

        // 本地侧只有「按日按渠道读取」这一次交互 —— 没有任何更新/状态迁移调用（Phase 1 不自动调账）
        verify(payOrderService).listPaidByDateAndChannel(BILL_DATE, "WECHAT");
        verifyNoMoreInteractions(payOrderService);
    }

    @Test
    @DisplayName("账单获取失败 — 落失败批次，不写正常批次（同日重跑时可被覆盖）")
    void shouldSaveFailedBatch_whenBillDownloadFails() {
        when(channelRouter.downloadWechatBill("2026-10-02"))
                .thenThrow(new IllegalStateException("bill not ready"));

        reconciliationManager.run(BILL_DATE, "WECHAT");

        verify(reconciliationService).saveFailedBatch(BILL_DATE, "WECHAT");
        verify(reconciliationService, never()).saveBatchResult(any(), anyString(), anyInt(), anyInt(), anyList());
        verifyNoMoreInteractions(payOrderService);
    }

    @Test
    @DisplayName("渠道缺省 — 当日全部渠道各生成一个批次（WECHAT + ALIPAY）")
    void shouldRunAllChannels_whenChannelAbsent() {
        when(channelRouter.downloadWechatBill("2026-10-02")).thenReturn(List.of());
        when(channelRouter.downloadAlipayBill("2026-10-02")).thenReturn(List.of());
        when(payOrderService.listPaidByDateAndChannel(eq(BILL_DATE), anyString())).thenReturn(List.of());
        when(reconciliationService.saveBatchResult(eq(BILL_DATE), anyString(), anyInt(), anyInt(), anyList()))
                .thenReturn(savedBatch("WECHAT"));

        List<ReconciliationBatchVO> batches = reconciliationManager.run(BILL_DATE, null);

        assertEquals(2, batches.size());
        verify(reconciliationService).saveBatchResult(eq(BILL_DATE), eq("WECHAT"), eq(0), eq(0), anyList());
        verify(reconciliationService).saveBatchResult(eq(BILL_DATE), eq("ALIPAY"), eq(0), eq(0), anyList());
    }

    @Test
    @DisplayName("渠道非法 — PARAM_ERROR（400），不下载任何账单")
    void shouldReject_whenChannelIllegal() {
        assertThrows(BadRequestException.class, () -> reconciliationManager.run(BILL_DATE, "UNIONPAY"));

        verify(reconciliationService, never()).saveBatchResult(any(), anyString(), anyInt(), anyInt(), anyList());
    }
}
