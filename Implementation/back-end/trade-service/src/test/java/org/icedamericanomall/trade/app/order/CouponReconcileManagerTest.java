package org.icedamericanomall.trade.app.order;

import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
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
 * {@link CouponReconcileManager} 兜底对账单元测试（V5.19 资损修复第三道网）。
 *
 * <p>覆盖判定方向的核心不变式：<b>只有确认 trade 侧不存在该订单时才回滚券</b>；
 * 任何不确定状态（订单查询失败 / 回滚失败）都必须保留标记下轮重试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponReconcileManager — 券核销兜底对账")
class CouponReconcileManagerTest {

    @Mock private CouponConsumptionLedger couponConsumptionLedger;
    @Mock private OrderRepository orderRepository;
    @Mock private CouponClient couponClient;

    private CouponReconcileManager manager;

    @BeforeEach
    void setUp() {
        manager = new CouponReconcileManager(couponConsumptionLedger, orderRepository, couponClient);
    }

    private void pending(String... orderNos) {
        when(couponConsumptionLedger.listPendingBefore(anyLong(), anyInt()))
                .thenReturn(List.of(orderNos));
    }

    @Test
    @DisplayName("订单不存在（进程中途退出）→ 回滚券 + 清除标记")
    void shouldRollbackCoupon_whenOrderMissing() {
        pending("ORD-GONE");
        when(orderRepository.findByOrderNo("ORD-GONE")).thenReturn(null);

        int reclaimed = manager.reconcile();

        assertEquals(1, reclaimed);
        verify(couponClient).rollbackByOrderNo("ORD-GONE");
        verify(couponConsumptionLedger).clear("ORD-GONE");
    }

    @Test
    @DisplayName("订单已成交 → 只清标记，绝不回滚券（防反向资损：用户既得单又得券）")
    void shouldNotRollbackCoupon_whenOrderExists() {
        pending("ORD-OK");
        when(orderRepository.findByOrderNo("ORD-OK")).thenReturn(new Order());

        int reclaimed = manager.reconcile();

        assertEquals(0, reclaimed);
        verify(couponClient, never()).rollbackByOrderNo(anyString());
        verify(couponConsumptionLedger).clear("ORD-OK");
    }

    @Test
    @DisplayName("券回滚失败 → 保留标记待下轮重试，且不抛出（任务级健壮）")
    void shouldKeepPendingMarker_whenRollbackFails() {
        pending("ORD-RETRY");
        when(orderRepository.findByOrderNo("ORD-RETRY")).thenReturn(null);
        doThrow(new RuntimeException("marketing down")).when(couponClient).rollbackByOrderNo("ORD-RETRY");

        assertDoesNotThrow(() -> manager.reconcile());

        verify(couponConsumptionLedger, never()).clear("ORD-RETRY");
    }

    @Test
    @DisplayName("订单查询失败（DB 不可用）→ 不回滚也不清标记（状态不确定时不得判定）")
    void shouldKeepPendingMarker_whenOrderLookupFails() {
        pending("ORD-DB-DOWN");
        when(orderRepository.findByOrderNo("ORD-DB-DOWN")).thenThrow(new RuntimeException("DB down"));

        assertDoesNotThrow(() -> manager.reconcile());

        verify(couponClient, never()).rollbackByOrderNo(anyString());
        verify(couponConsumptionLedger, never()).clear(anyString());
    }

    @Test
    @DisplayName("无可对账标记 → 不触碰订单与券侧（空转零开销）")
    void shouldDoNothing_whenNoPendingMarker() {
        pending();

        assertEquals(0, manager.reconcile());

        verifyNoInteractions(orderRepository);
        verifyNoInteractions(couponClient);
    }

    @Test
    @DisplayName("宽限窗口 — 只扫描早于 now-10min 的标记，避免与在途下单请求竞态")
    void shouldScanOnlyMarkersOlderThanGraceWindow() {
        pending();

        manager.reconcile();

        ArgumentCaptor<Long> cutoff = ArgumentCaptor.forClass(Long.class);
        verify(couponConsumptionLedger).listPendingBefore(cutoff.capture(), eq(CouponReconcileManager.BATCH_SIZE));
        long expected = System.currentTimeMillis() - CouponReconcileManager.GRACE.toMillis();
        assertTrue(Math.abs(cutoff.getValue() - expected) < 5_000,
                "cutoff 应约为 now - 10min，实际=" + cutoff.getValue());
    }

    @Test
    @DisplayName("多条标记 → 部分可处理部分失败：失败项保留、其余照常回收（单条异常不中断整轮）")
    void shouldHandleEachMarkerIndependently() {
        // 失败项排在前面：若实现的 try/catch 落在循环之外（单条异常即中断整轮），ORD-A 不会被回收
        pending("ORD-B", "ORD-A");
        when(orderRepository.findByOrderNo("ORD-B")).thenReturn(null);
        when(orderRepository.findByOrderNo("ORD-A")).thenReturn(null);
        // 成功分支必须显式打桩：strict stubs 下「同方法、不同实参」的调用会抛 PotentialStubbingProblem，
        // 而那会被实现的 catch 吞掉 —— 不显式打桩则成功路径不可观测（会把两颗标记都误判成失败）
        doNothing().when(couponClient).rollbackByOrderNo("ORD-A");
        doThrow(new RuntimeException("marketing down")).when(couponClient).rollbackByOrderNo("ORD-B");

        int reclaimed = manager.reconcile();

        assertEquals(1, reclaimed, "只有 A 成功回收");
        verify(couponClient).rollbackByOrderNo("ORD-B");
        verify(couponClient).rollbackByOrderNo("ORD-A");
        verify(couponConsumptionLedger).clear("ORD-A");
        verify(couponConsumptionLedger, never()).clear("ORD-B");
    }
}
