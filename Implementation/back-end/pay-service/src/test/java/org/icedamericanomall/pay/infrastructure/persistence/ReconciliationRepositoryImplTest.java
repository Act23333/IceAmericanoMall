package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.icedamericanomall.pay.domain.enums.ReconciliationStatusEnum;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationBatch;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationDetail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对账仓储实现单元测试（F4）—— 锁死「先删后写」的幂等口径与批次/明细的字段回填。
 *
 * <p>用 Mapper 打桩（PO 类型），实现类内部完成 PO ↔ 聚合转换；
 * 「同日同渠道先删后写」这一业务规则在纯单测中被验证（这也是本类不继承
 * {@code ServiceImpl} 的原因，见实现类 Javadoc）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReconciliationRepositoryImpl — 先删后写与批次落库")
class ReconciliationRepositoryImplTest {

    private static final LocalDate BILL_DATE = LocalDate.of(2026, 10, 2);

    @Mock ReconciliationBatchMapper batchMapper;
    @Mock ReconciliationDetailMapper detailMapper;
    @InjectMocks ReconciliationRepositoryImpl repository;

    /**
     * 初始化 MP 实体元数据 —— 让 lambda 包装器能解析「属性 → 列名」并生成带绑定参数的 SQL。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ReconciliationBatchPO.class);
        TableInfoHelper.initTableInfo(assistant, ReconciliationDetailPO.class);
    }

    private ReconciliationDetail detail(String payOrderNo, int result) {
        ReconciliationDetail detail = new ReconciliationDetail();
        detail.setPayOrderNo(payOrderNo);
        detail.setResult(result);
        return detail;
    }

    /** 模拟自增主键回填（真实 Mapper 由数据库回填）。 */
    private void stubInsertReturnsId(long id) {
        doAnswer(invocation -> {
            ((ReconciliationBatchPO) invocation.getArgument(0)).setId(id);
            return 1;
        }).when(batchMapper).insert(any(ReconciliationBatchPO.class));
    }

    @Test
    @DisplayName("首次落库 — 无旧批次：插入完成批次并回填明细 batchId")
    void shouldInsertBatchAndDetails_whenNoExistingBatch() {
        when(batchMapper.selectList(any())).thenReturn(List.of());
        stubInsertReturnsId(11L);
        List<ReconciliationDetail> details = List.of(detail("pay-001", 1), detail("pay-00X", 2));

        ReconciliationBatch batch = repository.saveBatchResult(BILL_DATE, "WECHAT", 34800, 30700, details);

        assertEquals(11L, batch.getId());
        assertEquals(ReconciliationStatusEnum.COMPLETED.getCode(), batch.getStatus());
        assertEquals(BILL_DATE, batch.getBillDate());
        assertEquals("WECHAT", batch.getChannel());
        assertEquals(34800, batch.getChannelTotal());
        assertEquals(30700, batch.getLocalTotal());
        assertEquals(11L, details.get(0).getBatchId(), "明细必须挂到本次批次");
        assertEquals(11L, details.get(1).getBatchId());
        verify(detailMapper, never()).delete(any());
        verify(batchMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("重复执行同日对账 — 先删旧明细与旧批次，再写新批次（先删后写幂等）")
    void shouldDeleteThenRewrite_whenExistingBatch() {
        ReconciliationBatchPO existing = new ReconciliationBatchPO();
        existing.setId(7L);
        when(batchMapper.selectList(any())).thenReturn(List.of(existing));
        stubInsertReturnsId(12L);

        ReconciliationBatch batch = repository.saveBatchResult(BILL_DATE, "WECHAT", 100, 100,
                List.of(detail("pay-001", 1)));

        verify(detailMapper).delete(any());
        verify(batchMapper).deleteById(7L);
        verify(batchMapper).insert(any(ReconciliationBatchPO.class));
        assertEquals(12L, batch.getId(), "重写后的批次是新行（uk_date_channel 保证同日同渠道唯一）");
    }

    @Test
    @DisplayName("账单获取失败 — 落失败批次（总额为 null），同样先删后写")
    void shouldSaveFailedBatch_withNullTotals() {
        ReconciliationBatchPO existing = new ReconciliationBatchPO();
        existing.setId(7L);
        when(batchMapper.selectList(any())).thenReturn(List.of(existing));
        stubInsertReturnsId(13L);

        ReconciliationBatch batch = repository.saveFailedBatch(BILL_DATE, "ALIPAY");

        verify(batchMapper).deleteById(7L);
        assertEquals(ReconciliationStatusEnum.FAILED.getCode(), batch.getStatus());
        assertNull(batch.getChannelTotal());
        assertNull(batch.getLocalTotal());
        verify(detailMapper, never()).insert(any(ReconciliationDetailPO.class));
    }

    @Test
    @DisplayName("批次分页 — 渠道/状态/账单日过滤条件全部进入 SQL 参数")
    @SuppressWarnings("unchecked")
    void shouldFilterBatches_whenConditionsProvided() {
        doReturn(new Page<ReconciliationBatchPO>(2, 50)).when(batchMapper)
                .selectPage(any(Page.class), any(Wrapper.class));

        IPage<ReconciliationBatch> result = repository.pageBatches(2, 50, "WECHAT", 1, BILL_DATE);

        assertTrue(result.getRecords().isEmpty(), "PO 页转换为领域分页（空桩 → 空记录）");
        ArgumentCaptor<Page<ReconciliationBatchPO>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<AbstractWrapper> wrapperCaptor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(batchMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertEquals(2, pageCaptor.getValue().getCurrent());
        assertEquals(50, pageCaptor.getValue().getSize());
        // 触发 SQL 生成（MP 的绑定参数是惰性注册的），再断言过滤条件确实进入 WHERE
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("channel"), "SQL 应包含渠道过滤: " + sql);
        assertTrue(sql.contains("status"), "SQL 应包含状态过滤: " + sql);
        assertTrue(sql.contains("bill_date"), "SQL 应包含账单日过滤: " + sql);
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue("WECHAT"));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(1));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(BILL_DATE));
    }

    @Test
    @DisplayName("明细分页 — 批次 ID 与结果四态过滤进入 SQL 参数")
    @SuppressWarnings("unchecked")
    void shouldFilterDetails_whenResultProvided() {
        doReturn(new Page<ReconciliationDetailPO>(1, 20)).when(detailMapper)
                .selectPage(any(Page.class), any(Wrapper.class));

        IPage<ReconciliationDetail> result = repository.pageDetails(5L, 1, 20, 4);

        assertTrue(result.getRecords().isEmpty(), "PO 页转换为领域分页（空桩 → 空记录）");
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<AbstractWrapper> wrapperCaptor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(detailMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("batch_id"), "SQL 应包含批次过滤: " + sql);
        assertTrue(sql.contains("result"), "SQL 应包含四态过滤: " + sql);
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(5L));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(4));
    }
}
