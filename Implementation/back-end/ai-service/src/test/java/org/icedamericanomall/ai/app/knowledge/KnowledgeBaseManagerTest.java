package org.icedamericanomall.ai.app.knowledge;

import org.icedamericanomall.ai.app.dto.KnowledgeDTO;
import org.icedamericanomall.ai.domain.model.knowledge.KnowledgeEntry;
import org.icedamericanomall.ai.domain.model.knowledge.KnowledgeVectorDoc;
import org.icedamericanomall.ai.domain.port.KnowledgeVectorPort;
import org.icedamericanomall.ai.domain.repository.MerchantKnowledgeRepository;
import org.icedamericanomall.ai.infrastructure.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * KnowledgeBaseManager 单元测试 —— 知识库 CRUD 与向量索引同步的降级/开关语义。
 *
 * <p>覆盖：
 * <ul>
 *   <li>摄取成功 → 回填 {@code es:{knowledgeId}}；</li>
 *   <li>摄取降级（返回哨兵 / 抛异常）→ CRUD 不受影响、不写 embeddingId；</li>
 *   <li>删除 → 清理索引；</li>
 *   <li>{@code ai.rag.enabled=false} → 端口零触达（行为与现状一致）。</li>
 * </ul>
 *
 * <p>仓储以 {@link MerchantKnowledgeRepository} 端口打桩（PO 映射不在本层）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeBaseManagerTest {

    @Mock
    private MerchantKnowledgeRepository knowledgeRepository;

    @Mock
    private KnowledgeVectorPort knowledgeVectorPort;

    @Mock
    private AiProperties aiProperties;

    private KnowledgeBaseManager manager;

    @BeforeEach
    void setUp() {
        manager = new KnowledgeBaseManager(knowledgeRepository, knowledgeVectorPort, aiProperties);
        // 插入时模拟自增主键回填
        when(knowledgeRepository.save(any(KnowledgeEntry.class))).thenAnswer(invocation -> {
            KnowledgeEntry entry = invocation.getArgument(0);
            entry.setId(100L);
            return entry;
        });
    }

    @Test
    @DisplayName("创建 — RAG 开启 + 摄取成功 → 端口收到原文，embeddingId 回填 es:100")
    void shouldSyncVectorIndex_whenRagEnabledAndUpsertSucceeds() {
        when(aiProperties.getRag()).thenReturn(rag(true));
        when(knowledgeVectorPort.upsertKnowledgeVector(any())).thenReturn(3);

        KnowledgeDTO dto = manager.create(9L, "退货政策", "7天无理由", "POLICY");

        assertNotNull(dto);
        assertEquals(100L, dto.getId());
        ArgumentCaptor<KnowledgeVectorDoc> docCaptor = ArgumentCaptor.forClass(KnowledgeVectorDoc.class);
        verify(knowledgeVectorPort).upsertKnowledgeVector(docCaptor.capture());
        assertEquals(100L, docCaptor.getValue().knowledgeId());
        assertEquals(9L, docCaptor.getValue().sellerId());
        assertEquals("7天无理由", docCaptor.getValue().content());

        ArgumentCaptor<KnowledgeEntry> entryCaptor = ArgumentCaptor.forClass(KnowledgeEntry.class);
        verify(knowledgeRepository, atLeastOnce()).update(entryCaptor.capture());
        assertEquals("es:100", entryCaptor.getValue().getEmbeddingId());
    }

    @Test
    @DisplayName("创建 — 摄取抛异常 → CRUD 正常返回、不写 embeddingId（全链路降级）")
    void shouldNotBlockCreate_whenUpsertThrows() {
        when(aiProperties.getRag()).thenReturn(rag(true));
        when(knowledgeVectorPort.upsertKnowledgeVector(any()))
                .thenThrow(new IllegalStateException("search-service down"));

        KnowledgeDTO dto = manager.create(9L, "退货政策", "7天无理由", "POLICY");

        assertNotNull(dto);
        assertEquals(100L, dto.getId());
        verify(knowledgeRepository, never()).update(any(KnowledgeEntry.class));
    }

    @Test
    @DisplayName("创建 — 摄取返回失败哨兵(-1) → 不写 embeddingId")
    void shouldNotWriteEmbeddingId_whenUpsertReturnsSentinel() {
        when(aiProperties.getRag()).thenReturn(rag(true));
        when(knowledgeVectorPort.upsertKnowledgeVector(any()))
                .thenReturn(KnowledgeVectorPort.UPSERT_FAILED);

        KnowledgeDTO dto = manager.create(9L, "退货政策", "7天无理由", "POLICY");

        assertNotNull(dto);
        verify(knowledgeRepository, never()).update(any(KnowledgeEntry.class));
    }

    @Test
    @DisplayName("创建 — RAG 关闭 → 向量端口零触达（开关关闭=现状不变）")
    void shouldNotTouchVectorPort_whenRagDisabled() {
        when(aiProperties.getRag()).thenReturn(rag(false));

        manager.create(9L, "退货政策", "7天无理由", "POLICY");

        verifyNoInteractions(knowledgeVectorPort);
        verify(knowledgeRepository, never()).update(any(KnowledgeEntry.class));
    }

    @Test
    @DisplayName("更新 — 本人条目 → 落库后重写向量索引（幂等重写）")
    void shouldResyncVectorIndex_whenUpdateOwnItem() {
        when(aiProperties.getRag()).thenReturn(rag(true));
        when(knowledgeRepository.findByIdAndSeller(100L, 9L)).thenReturn(existingItem());
        when(knowledgeVectorPort.upsertKnowledgeVector(any())).thenReturn(2);

        boolean updated = manager.update(100L, 9L, "新标题", null, null);

        assertTrue(updated);
        verify(knowledgeVectorPort).upsertKnowledgeVector(any(KnowledgeVectorDoc.class));
    }

    @Test
    @DisplayName("更新 — 条目不存在 → false 且不触达向量端口")
    void shouldReturnFalse_whenUpdateUnknownItem() {
        when(knowledgeRepository.findByIdAndSeller(404L, 9L)).thenReturn(null);

        assertFalse(manager.update(404L, 9L, "标题", null, null));
        verifyNoInteractions(knowledgeVectorPort);
    }

    @Test
    @DisplayName("更新 — null-safe 合并：仅覆盖非空入参")
    void shouldMergePatchOnlyNonNullFields_whenUpdate() {
        when(aiProperties.getRag()).thenReturn(rag(false));
        when(knowledgeRepository.findByIdAndSeller(100L, 9L)).thenReturn(existingItem());

        manager.update(100L, 9L, "新标题", null, null);

        ArgumentCaptor<KnowledgeEntry> captor = ArgumentCaptor.forClass(KnowledgeEntry.class);
        verify(knowledgeRepository).update(captor.capture());
        assertEquals("新标题", captor.getValue().getTitle(), "非空标题应覆盖");
        assertEquals("7天无理由", captor.getValue().getContent(), "null 正文不应覆盖");
        assertEquals(2, captor.getValue().getStatus(), "状态不受更新影响");
    }

    @Test
    @DisplayName("删除 — RAG 开启 → 软删落库并清理向量索引")
    void shouldRemoveVectorIndex_whenSoftDelete() {
        when(aiProperties.getRag()).thenReturn(rag(true));
        KnowledgeEntry item = existingItem();
        when(knowledgeRepository.findByIdAndSeller(100L, 9L)).thenReturn(item);

        manager.softDelete(100L, 9L);

        assertEquals(3, item.getStatus(), "应为软删状态 3");
        verify(knowledgeVectorPort).deleteKnowledgeVector(100L);
    }

    @Test
    @DisplayName("删除 — 清理索引抛异常 → 不阻断软删")
    void shouldNotBlockSoftDelete_whenDeleteVectorThrows() {
        when(aiProperties.getRag()).thenReturn(rag(true));
        KnowledgeEntry item = existingItem();
        when(knowledgeRepository.findByIdAndSeller(100L, 9L)).thenReturn(item);
        when(knowledgeVectorPort.deleteKnowledgeVector(anyLong()))
                .thenThrow(new IllegalStateException("es down"));

        manager.softDelete(100L, 9L);

        assertEquals(3, item.getStatus());
    }

    @Test
    @DisplayName("删除 — RAG 关闭 → 向量端口零触达，embeddingId 不被读取")
    void shouldNotTouchVectorPortOnDelete_whenRagDisabled() {
        when(aiProperties.getRag()).thenReturn(rag(false));
        when(knowledgeRepository.findByIdAndSeller(100L, 9L)).thenReturn(existingItem());

        manager.softDelete(100L, 9L);

        verifyNoInteractions(knowledgeVectorPort);
    }

    @Test
    @DisplayName("出参 DTO 不含 embeddingId 字段（内部句柄不外泄）")
    void shouldNotExposeEmbeddingIdInDto() {
        assertThrows(NoSuchFieldException.class, () -> KnowledgeDTO.class.getDeclaredField("embeddingId"));
    }

    private static KnowledgeEntry existingItem() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(100L);
        entry.setSellerId(9L);
        entry.setTitle("退货政策");
        entry.setContent("7天无理由");
        entry.setStatus(2);
        return entry;
    }

    private static AiProperties.Rag rag(boolean enabled) {
        AiProperties.Rag rag = new AiProperties.Rag();
        rag.setEnabled(enabled);
        return rag;
    }
}
