package org.icedamericanomall.search.app;

import org.icedamericanomall.dto.NoteVectorHitCO;
import org.icedamericanomall.dto.NoteVectorUpsertCO;
import org.icedamericanomall.search.domain.gateway.EmbeddingGateway;
import org.icedamericanomall.search.domain.gateway.NoteVectorIndexGateway;
import org.icedamericanomall.search.domain.model.search.NoteVectorDoc;
import org.icedamericanomall.search.domain.model.search.NoteVectorHit;
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
 * NoteVectorManager 单元测试 —— 摄取（软删语义/文本拼接/幂等覆盖写）、相似检索（排除自身/归一）与降级。
 *
 * <p>Mock ES 笔记网关（{@link NoteVectorIndexGateway}）与 Embedding 网关（{@link EmbeddingGateway}），
 * 不起真 ES。镜像 {@code ProductVectorManagerTest}（ADR-046 管道第二次复用），
 * fail-open 降级语义与「deleted 优先于 Embedding 配置」的检查顺序在此锁死。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoteVectorManagerTest {

    private static final Long NOTE_ID = 9L;
    private static final String TITLE = "冲咖啡入门指南";
    private static final String CONTENT = "手冲水温 90 度";
    private static final float[] VECTOR_V1 = {0.1f, 0.2f};
    private static final float[] VECTOR_V2 = {0.3f, 0.4f};

    @Mock
    private EmbeddingGateway embeddingGateway;

    @Mock
    private NoteVectorIndexGateway noteVectorIndexGateway;

    private NoteVectorManager manager;

    @BeforeEach
    void setUp() {
        manager = new NoteVectorManager(embeddingGateway, noteVectorIndexGateway);
        when(embeddingGateway.isConfigured()).thenReturn(true);
        when(embeddingGateway.embed(any())).thenReturn(VECTOR_V1);
    }

    private NoteVectorUpsertCO co(String title, String content) {
        NoteVectorUpsertCO co = new NoteVectorUpsertCO();
        co.setNoteId(NOTE_ID);
        co.setTitle(title);
        co.setContent(content);
        return co;
    }

    private NoteVectorHit hit(Long noteId, Double score) {
        return new NoteVectorHit(noteId, score);
    }

    @Test
    @DisplayName("摄取 — 向量化文本为 title + content（单次单向量调用）")
    void shouldEmbedConcatenatedText_whenUpsert() {
        manager.upsert(co(TITLE, CONTENT));

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingGateway, times(1)).embed(textCaptor.capture());
        assertEquals(TITLE + " " + CONTENT, textCaptor.getValue());
        verify(noteVectorIndexGateway, times(1)).upsertVectorDoc(any());
    }

    @Test
    @DisplayName("摄取 — content 空白 → 只用 title（可选拼接不产生尾随空格）")
    void shouldUseTitleOnly_whenContentBlank() {
        manager.upsert(co(TITLE, "  "));

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingGateway).embed(textCaptor.capture());
        assertEquals(TITLE, textCaptor.getValue());
    }

    @Test
    @DisplayName("摄取 — deleted=true → 删除向量文档返回 1，不向量化不写文档")
    void shouldDeleteAndReturnOne_whenUpsertDeleted() {
        NoteVectorUpsertCO deletedCo = co(TITLE, CONTENT);
        deletedCo.setDeleted(true);

        int result = manager.upsert(deletedCo);

        assertEquals(1, result);
        verify(noteVectorIndexGateway, times(1)).deleteById(NOTE_ID);
        verify(embeddingGateway, never()).embed(any());
        verify(noteVectorIndexGateway, never()).upsertVectorDoc(any());
    }

    @Test
    @DisplayName("摄取 — deleted=true 优先于 Embedding 配置检查（删除是纯 ES 操作）")
    void shouldDeleteEvenWhenEmbeddingNotConfigured() {
        when(embeddingGateway.isConfigured()).thenReturn(false);
        NoteVectorUpsertCO deletedCo = co(TITLE, CONTENT);
        deletedCo.setDeleted(true);

        int result = manager.upsert(deletedCo);

        assertEquals(1, result);
        verify(noteVectorIndexGateway, times(1)).deleteById(NOTE_ID);
    }

    @Test
    @DisplayName("摄取 — 幂等覆盖写：同 noteId 重写整文档，向量以新向量为准")
    void shouldOverwriteWholeDocWithLatestVector_whenUpsertTwice() {
        when(embeddingGateway.embed(any())).thenReturn(VECTOR_V1, VECTOR_V2);

        int first = manager.upsert(co(TITLE, CONTENT));
        int second = manager.upsert(co(TITLE + "（更新版）", CONTENT));

        assertEquals(1, first);
        assertEquals(1, second);
        ArgumentCaptor<NoteVectorDoc> docCaptor = ArgumentCaptor.forClass(NoteVectorDoc.class);
        verify(noteVectorIndexGateway, times(2)).upsertVectorDoc(docCaptor.capture());
        List<NoteVectorDoc> docs = docCaptor.getAllValues();
        assertEquals(NOTE_ID, docs.get(0).noteId());
        assertEquals(NOTE_ID, docs.get(1).noteId());
        assertArrayEquals(toDoubles(VECTOR_V1), docs.get(0).embedding());
        assertArrayEquals(toDoubles(VECTOR_V2), docs.get(1).embedding());
        assertEquals(TITLE + "（更新版）", docs.get(1).title());
    }

    @Test
    @DisplayName("摄取 — 原文字段一并以入参为准落文档")
    void shouldCarryContentToDoc_whenUpsert() {
        manager.upsert(co(TITLE, CONTENT));

        ArgumentCaptor<NoteVectorDoc> docCaptor = ArgumentCaptor.forClass(NoteVectorDoc.class);
        verify(noteVectorIndexGateway).upsertVectorDoc(docCaptor.capture());
        NoteVectorDoc doc = docCaptor.getValue();
        assertEquals(NOTE_ID, doc.noteId());
        assertEquals(TITLE, doc.title());
        assertEquals(CONTENT, doc.content());
    }

    @Test
    @DisplayName("摄取 — Embedding 未配置 → 返回 0 且不向量化/不触达索引网关（未启用不报错）")
    void shouldReturnZero_whenEmbeddingNotConfigured() {
        when(embeddingGateway.isConfigured()).thenReturn(false);

        int result = manager.upsert(co(TITLE, CONTENT));

        assertEquals(0, result);
        verify(embeddingGateway, never()).embed(any());
        verifyNoInteractions(noteVectorIndexGateway);
    }

    @Test
    @DisplayName("相似检索 — 自身无向量（冷启动未摄取）→ 空列表且不发起 kNN")
    void shouldReturnEmpty_whenSelfVectorMissing() {
        when(noteVectorIndexGateway.findVectorById(NOTE_ID)).thenReturn(Optional.empty());

        List<NoteVectorHitCO> hits = manager.similar(NOTE_ID, 5);

        assertTrue(hits.isEmpty());
        verify(noteVectorIndexGateway, never()).similar(any(), anyInt());
    }

    @Test
    @DisplayName("相似检索 — 自身向量为空数组 → 同无向量处理")
    void shouldReturnEmpty_whenSelfVectorBlank() {
        when(noteVectorIndexGateway.findVectorById(NOTE_ID)).thenReturn(Optional.of(new double[0]));

        assertTrue(manager.similar(NOTE_ID, 5).isEmpty());
        verify(noteVectorIndexGateway, never()).similar(any(), anyInt());
    }

    @Test
    @DisplayName("相似检索 — 命中含自身 → 过滤自身，出 noteId + score")
    void shouldFilterSelf_whenHitsContainSelf() {
        when(noteVectorIndexGateway.findVectorById(NOTE_ID)).thenReturn(Optional.of(toDoubles(VECTOR_V1)));
        when(noteVectorIndexGateway.similar(any(), anyInt())).thenReturn(List.of(
                hit(NOTE_ID, 1.0), hit(100L, 0.9), hit(200L, 0.8)));

        List<NoteVectorHitCO> hits = manager.similar(NOTE_ID, 5);

        assertEquals(2, hits.size());
        assertEquals(100L, hits.get(0).getNoteId());
        assertEquals(0.9, hits.get(0).getScore());
        assertEquals(200L, hits.get(1).getNoteId());
        assertEquals(0.8, hits.get(1).getScore());
    }

    @Test
    @DisplayName("相似检索 — size=5 → kNN 多取 1 条（Top-K=6），自身未在命中时截回 5 条")
    void shouldLimitToRequestedSize_whenSelfNotInHits() {
        when(noteVectorIndexGateway.findVectorById(NOTE_ID)).thenReturn(Optional.of(toDoubles(VECTOR_V1)));
        when(noteVectorIndexGateway.similar(any(), eq(6))).thenReturn(List.of(
                hit(100L, 0.9), hit(101L, 0.9), hit(102L, 0.9),
                hit(103L, 0.9), hit(104L, 0.9), hit(105L, 0.9)));

        List<NoteVectorHitCO> hits = manager.similar(NOTE_ID, 5);

        assertEquals(5, hits.size());
        verify(noteVectorIndexGateway).similar(any(), eq(6));
    }

    @Test
    @DisplayName("相似检索 — size ≤0 归一 5（Top-K=6）")
    void shouldNormalizeSizeToDefault_whenSizeInvalid() {
        when(noteVectorIndexGateway.findVectorById(NOTE_ID)).thenReturn(Optional.of(toDoubles(VECTOR_V1)));
        when(noteVectorIndexGateway.similar(any(), anyInt())).thenReturn(List.of());

        manager.similar(NOTE_ID, 0);
        manager.similar(NOTE_ID, -3);
        manager.similar(NOTE_ID, null);

        verify(noteVectorIndexGateway, times(3)).similar(any(), eq(6));
    }

    @Test
    @DisplayName("相似检索 — size >20 截断为 20（Top-K=21）")
    void shouldCapSize_whenOverTwenty() {
        when(noteVectorIndexGateway.findVectorById(NOTE_ID)).thenReturn(Optional.of(toDoubles(VECTOR_V1)));
        when(noteVectorIndexGateway.similar(any(), anyInt())).thenReturn(List.of());

        manager.similar(NOTE_ID, 25);

        verify(noteVectorIndexGateway).similar(any(), eq(21));
    }

    private static double[] toDoubles(float[] vector) {
        double[] doubles = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            doubles[i] = vector[i];
        }
        return doubles;
    }
}
