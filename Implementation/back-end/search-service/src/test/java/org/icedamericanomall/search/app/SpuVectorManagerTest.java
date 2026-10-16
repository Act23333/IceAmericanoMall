package org.icedamericanomall.search.app;

import org.icedamericanomall.dto.SpuVectorHitCO;
import org.icedamericanomall.dto.SpuVectorUpsertCO;
import org.icedamericanomall.search.domain.gateway.EmbeddingGateway;
import org.icedamericanomall.search.domain.gateway.SpuSearchGateway;
import org.icedamericanomall.search.domain.model.search.SpuSearchHit;
import org.icedamericanomall.search.domain.model.search.SpuVectorDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SpuVectorManager 单元测试 —— 摄取（文本拼接/单向量/幂等覆盖写）、相似检索（排除自身/归一）与降级。
 *
 * <p>Mock ES 商品网关（{@link SpuSearchGateway}）与 Embedding 网关（{@link EmbeddingGateway}），
 * 不起真 ES。fail-open 降级语义（ADR-046）在此锁死。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpuVectorManagerTest {

    private static final Long SPU_ID = 9L;
    private static final String NAME = "无线蓝牙耳机";
    private static final String DESCRIPTION = "主动降噪 头戴式";
    private static final String BRAND = "SoundMax";
    private static final float[] VECTOR_V1 = {0.1f, 0.2f};
    private static final float[] VECTOR_V2 = {0.3f, 0.4f};

    @Mock
    private EmbeddingGateway embeddingGateway;

    @Mock
    private SpuSearchGateway spuSearchGateway;

    private SpuVectorManager manager;

    @BeforeEach
    void setUp() {
        manager = new SpuVectorManager(embeddingGateway, spuSearchGateway);
        when(embeddingGateway.isConfigured()).thenReturn(true);
        when(embeddingGateway.embed(any())).thenReturn(VECTOR_V1);
    }

    private SpuVectorUpsertCO co(String name, String description, String brand) {
        SpuVectorUpsertCO co = new SpuVectorUpsertCO();
        co.setSpuId(SPU_ID);
        co.setName(name);
        co.setDescription(description);
        co.setBrand(brand);
        co.setCategoryId(1L);
        co.setMainImage("img");
        co.setPrice(12900);
        co.setSoldCount(3);
        return co;
    }

    private SpuSearchHit hit(Long id, Double score) {
        return new SpuSearchHit(id, "biz-" + id, 1L, "n", "d", "b", "img", 100, 5, score);
    }

    @Test
    @DisplayName("摄取 — 向量化文本为 name + description + brand（单次单向量调用）")
    void shouldEmbedConcatenatedText_whenUpsert() {
        manager.upsert(co(NAME, DESCRIPTION, BRAND));

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingGateway, times(1)).embed(textCaptor.capture());
        assertEquals(NAME + " " + DESCRIPTION + " " + BRAND, textCaptor.getValue());
        verify(spuSearchGateway, times(1)).upsertVectorDoc(any());
    }

    @Test
    @DisplayName("摄取 — description/brand 空白 → 只用 name（可选拼接不产生尾随空格）")
    void shouldUseNameOnly_whenOptionalPartsBlank() {
        manager.upsert(co(NAME, "  ", ""));

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingGateway).embed(textCaptor.capture());
        assertEquals(NAME, textCaptor.getValue());
    }

    @Test
    @DisplayName("摄取 — 幂等覆盖写：同 spuId 重写整文档，向量以新向量为准")
    void shouldOverwriteWholeDocWithLatestVector_whenUpsertTwice() {
        when(embeddingGateway.embed(any())).thenReturn(VECTOR_V1, VECTOR_V2);

        int first = manager.upsert(co(NAME, DESCRIPTION, BRAND));
        int second = manager.upsert(co(NAME + "（新款）", DESCRIPTION, BRAND));

        assertEquals(1, first);
        assertEquals(1, second);
        ArgumentCaptor<SpuVectorDoc> docCaptor = ArgumentCaptor.forClass(SpuVectorDoc.class);
        verify(spuSearchGateway, times(2)).upsertVectorDoc(docCaptor.capture());
        List<SpuVectorDoc> docs = docCaptor.getAllValues();
        assertEquals(SPU_ID, docs.get(0).spuId());
        assertEquals(SPU_ID, docs.get(1).spuId());
        assertArrayEquals(toDoubles(VECTOR_V1), docs.get(0).embedding());
        assertArrayEquals(toDoubles(VECTOR_V2), docs.get(1).embedding());
        assertEquals(NAME + "（新款）", docs.get(1).name());
    }

    @Test
    @DisplayName("摄取 — 展示字段一并以入参为准落文档")
    void shouldCarryDisplayFieldsToDoc_whenUpsert() {
        manager.upsert(co(NAME, DESCRIPTION, BRAND));

        ArgumentCaptor<SpuVectorDoc> docCaptor = ArgumentCaptor.forClass(SpuVectorDoc.class);
        verify(spuSearchGateway).upsertVectorDoc(docCaptor.capture());
        SpuVectorDoc doc = docCaptor.getValue();
        assertEquals(1L, doc.categoryId());
        assertEquals("img", doc.mainImage());
        assertEquals(12900, doc.price());
        assertEquals(3, doc.soldCount());
    }

    @Test
    @DisplayName("摄取 — Embedding 未配置 → 返回 0 且不向量化/不写 ES（未启用不报错）")
    void shouldReturnZero_whenEmbeddingNotConfigured() {
        when(embeddingGateway.isConfigured()).thenReturn(false);

        int result = manager.upsert(co(NAME, DESCRIPTION, BRAND));

        assertEquals(0, result);
        verify(embeddingGateway, never()).embed(any());
        verifyNoInteractions(spuSearchGateway);
    }

    @Test
    @DisplayName("相似检索 — 自身无向量（冷启动未摄取）→ 空列表且不发起 kNN")
    void shouldReturnEmpty_whenSelfVectorMissing() {
        when(spuSearchGateway.findVectorById(SPU_ID)).thenReturn(Optional.empty());

        List<SpuVectorHitCO> hits = manager.similar(SPU_ID, 5);

        assertTrue(hits.isEmpty());
        verify(spuSearchGateway, never()).vectorSearch(any(), anyInt());
    }

    @Test
    @DisplayName("相似检索 — 自身向量为空数组 → 同无向量处理")
    void shouldReturnEmpty_whenSelfVectorBlank() {
        when(spuSearchGateway.findVectorById(SPU_ID)).thenReturn(Optional.of(new double[0]));

        assertTrue(manager.similar(SPU_ID, 5).isEmpty());
        verify(spuSearchGateway, never()).vectorSearch(any(), anyInt());
    }

    @Test
    @DisplayName("相似检索 — 命中含自身 → 过滤自身，出 spuId + score")
    void shouldFilterSelf_whenHitsContainSelf() {
        when(spuSearchGateway.findVectorById(SPU_ID)).thenReturn(Optional.of(toDoubles(VECTOR_V1)));
        when(spuSearchGateway.vectorSearch(any(), anyInt())).thenReturn(List.of(
                hit(SPU_ID, 1.0), hit(100L, 0.9), hit(200L, 0.8)));

        List<SpuVectorHitCO> hits = manager.similar(SPU_ID, 5);

        assertEquals(2, hits.size());
        assertEquals(100L, hits.get(0).getSpuId());
        assertEquals(0.9, hits.get(0).getScore());
        assertEquals(200L, hits.get(1).getSpuId());
        assertEquals(0.8, hits.get(1).getScore());
    }

    @Test
    @DisplayName("相似检索 — size=5 → kNN 多取 1 条（Top-K=6），自身未在命中时截回 5 条")
    void shouldLimitToRequestedSize_whenSelfNotInHits() {
        when(spuSearchGateway.findVectorById(SPU_ID)).thenReturn(Optional.of(toDoubles(VECTOR_V1)));
        when(spuSearchGateway.vectorSearch(any(), eq(6))).thenReturn(List.of(
                hit(100L, 0.9), hit(101L, 0.9), hit(102L, 0.9),
                hit(103L, 0.9), hit(104L, 0.9), hit(105L, 0.9)));

        List<SpuVectorHitCO> hits = manager.similar(SPU_ID, 5);

        assertEquals(5, hits.size());
        verify(spuSearchGateway).vectorSearch(any(), eq(6));
    }

    @Test
    @DisplayName("相似检索 — size ≤0 归一 5（Top-K=6）")
    void shouldNormalizeSizeToDefault_whenSizeInvalid() {
        when(spuSearchGateway.findVectorById(SPU_ID)).thenReturn(Optional.of(toDoubles(VECTOR_V1)));
        when(spuSearchGateway.vectorSearch(any(), anyInt())).thenReturn(List.of());

        manager.similar(SPU_ID, 0);
        manager.similar(SPU_ID, -3);
        manager.similar(SPU_ID, null);

        verify(spuSearchGateway, times(3)).vectorSearch(any(), eq(6));
    }

    @Test
    @DisplayName("相似检索 — size >20 截断为 20（Top-K=21）")
    void shouldCapSize_whenOverTwenty() {
        when(spuSearchGateway.findVectorById(SPU_ID)).thenReturn(Optional.of(toDoubles(VECTOR_V1)));
        when(spuSearchGateway.vectorSearch(any(), anyInt())).thenReturn(List.of());

        manager.similar(SPU_ID, 25);

        verify(spuSearchGateway).vectorSearch(any(), eq(21));
    }

    private static double[] toDoubles(float[] vector) {
        double[] doubles = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            doubles[i] = vector[i];
        }
        return doubles;
    }
}
