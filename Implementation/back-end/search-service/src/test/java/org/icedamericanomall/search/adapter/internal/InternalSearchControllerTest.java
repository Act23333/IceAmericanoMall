package org.icedamericanomall.search.adapter.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.icedamericanomall.dto.NoteVectorHitCO;
import org.icedamericanomall.dto.NoteVectorUpsertCO;
import org.icedamericanomall.dto.SpuVectorHitCO;
import org.icedamericanomall.dto.SpuVectorUpsertCO;
import org.icedamericanomall.search.adapter.assembler.SpuSearchConverter;
import org.icedamericanomall.search.app.KnowledgeVectorManager;
import org.icedamericanomall.search.app.NoteVectorManager;
import org.icedamericanomall.search.app.SpuVectorManager;
import org.icedamericanomall.search.app.SearchManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalSearchController 商品/笔记向量端点测试 —— 委托、条件装配降级（0/空列表）与入参校验。
 *
 * <p>Mock app 层与 {@link ObjectProvider}（ES 关闭时 Manager Bean 不存在的口径），
 * standalone MockMvc 不起 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalSearchControllerTest {

    private static final String UPSERT_URL = "/internal/search/vector/spu/upsert";
    private static final String SIMILAR_URL = "/internal/search/vector/spu/similar";
    private static final String NOTE_UPSERT_URL = "/internal/search/vector/note/upsert";
    private static final String NOTE_SIMILAR_URL = "/internal/search/vector/note/similar";
    /** 商品 <b>业务键</b>（32-hex，ADR-061 §决策 2：I47 幂等键 / I48 锚点与命中的线格式）。 */
    private static final String SPU_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    /** 命中商品业务键（与锚点刻意不同值）。 */
    private static final String HIT_SPU_ID = "112233445566778899aabbccddeeff00";
    private static final Long NOTE_ID = 9L;

    @Mock
    private SearchManager searchManager;

    @Mock
    private SpuSearchConverter converter;

    @Mock
    private KnowledgeVectorManager knowledgeVectorManager;

    @Mock
    private SpuVectorManager spuVectorManager;

    @Mock
    private NoteVectorManager noteVectorManager;

    @Mock
    private ObjectProvider<KnowledgeVectorManager> knowledgeVectorProvider;

    @Mock
    private ObjectProvider<SpuVectorManager> spuVectorProvider;

    @Mock
    private ObjectProvider<NoteVectorManager> noteVectorProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalSearchController(
                searchManager, converter, knowledgeVectorProvider, spuVectorProvider,
                noteVectorProvider)).build();
    }

    @Test
    @DisplayName("I47 摄取 — Manager 可用 → 委托并返回 1（Req→CO 字段对齐）")
    void shouldDelegateToManager_whenUpsertAndVectorEnabled() throws Exception {
        when(spuVectorProvider.getIfAvailable()).thenReturn(spuVectorManager);

        Map<String, Object> body = new HashMap<>();
        body.put("spuId", SPU_ID);
        body.put("spuName", "无线蓝牙耳机");
        body.put("description", "主动降噪");
        body.put("price", 12900);
        mockMvc.perform(post(UPSERT_URL).contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        ArgumentCaptor<SpuVectorUpsertCO> captor = ArgumentCaptor.forClass(SpuVectorUpsertCO.class);
        verify(spuVectorManager).upsert(captor.capture());
        assertEquals(SPU_ID, captor.getValue().getSpuId());
        assertEquals("无线蓝牙耳机", captor.getValue().getSpuName());
        assertEquals("主动降噪", captor.getValue().getDescription());
        assertEquals(12900, captor.getValue().getPrice());
    }

    @Test
    @DisplayName("I47 摄取 — ES 关闭（Bean 不存在）→ 返回 0 且不触达 Manager")
    void shouldReturnZero_whenUpsertAndVectorUnavailable() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("spuId", SPU_ID);
        body.put("spuName", "无线蓝牙耳机");

        mockMvc.perform(post(UPSERT_URL).contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verifyNoInteractions(spuVectorManager);
    }

    @Test
    @DisplayName("I47 摄取 — 缺 spuId → 400")
    void shouldReturn400_whenUpsertMissingSpuId() throws Exception {
        mockMvc.perform(post(UPSERT_URL).contentType("application/json")
                        .content("{\"name\":\"无线蓝牙耳机\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(spuVectorManager);
    }

    @Test
    @DisplayName("I47 摄取 — 缺 spuName → 400")
    void shouldReturn400_whenUpsertMissingName() throws Exception {
        mockMvc.perform(post(UPSERT_URL).contentType("application/json")
                        .content("{\"spuId\":9}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(spuVectorManager);
    }

    @Test
    @DisplayName("I48 相似检索 — Manager 可用 → 委托并返回命中 spuId（业务键）+ score")
    void shouldDelegateToManager_whenSimilarAndVectorEnabled() throws Exception {
        when(spuVectorProvider.getIfAvailable()).thenReturn(spuVectorManager);
        when(spuVectorManager.similar(SPU_ID, 5)).thenReturn(List.of(
                hitCO(HIT_SPU_ID, 0.9), hitCO(HIT_SPU_ID, 0.8)));

        mockMvc.perform(get(SIMILAR_URL).param("spu_id", SPU_ID).param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spuId").value(HIT_SPU_ID))
                .andExpect(jsonPath("$[0].score").value(0.9))
                .andExpect(jsonPath("$[1].spuId").value(HIT_SPU_ID))
                .andExpect(jsonPath("$[1].score").value(0.8))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("I48 相似检索 — ES 关闭（Bean 不存在）→ 空列表且不触达 Manager")
    void shouldReturnEmptyList_whenSimilarAndVectorUnavailable() throws Exception {
        mockMvc.perform(get(SIMILAR_URL).param("spu_id", SPU_ID))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("[]",
                        result.getResponse().getContentAsString()));
        verifyNoInteractions(spuVectorManager);
    }

    @Test
    @DisplayName("I50 笔记摄取 — Manager 可用 → 委托并返回 1（Req→CO 字段对齐，deleted 透传）")
    void shouldDelegateToManager_whenNoteUpsertAndVectorEnabled() throws Exception {
        when(noteVectorProvider.getIfAvailable()).thenReturn(noteVectorManager);
        when(noteVectorManager.upsert(any(NoteVectorUpsertCO.class))).thenReturn(1);

        Map<String, Object> body = new HashMap<>();
        body.put("noteId", NOTE_ID);
        body.put("title", "冲咖啡入门指南");
        body.put("content", "手冲水温 90 度");
        mockMvc.perform(post(NOTE_UPSERT_URL).contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(1));

        ArgumentCaptor<NoteVectorUpsertCO> captor = ArgumentCaptor.forClass(NoteVectorUpsertCO.class);
        verify(noteVectorManager).upsert(captor.capture());
        assertEquals(NOTE_ID, captor.getValue().getNoteId());
        assertEquals("冲咖啡入门指南", captor.getValue().getTitle());
        assertEquals("手冲水温 90 度", captor.getValue().getContent());
        assertTrue(!captor.getValue().isDeleted());
    }

    @Test
    @DisplayName("I50 笔记摄取 — deleted=true 透传（软删/取消发布语义）")
    void shouldPassThroughDeletedFlag_whenNoteUpsertDeleted() throws Exception {
        when(noteVectorProvider.getIfAvailable()).thenReturn(noteVectorManager);

        mockMvc.perform(post(NOTE_UPSERT_URL).contentType("application/json")
                        .content("{\"noteId\":9,\"title\":\"标题\",\"deleted\":true}"))
                .andExpect(status().isOk());

        ArgumentCaptor<NoteVectorUpsertCO> captor = ArgumentCaptor.forClass(NoteVectorUpsertCO.class);
        verify(noteVectorManager).upsert(captor.capture());
        assertTrue(captor.getValue().isDeleted());
    }

    @Test
    @DisplayName("I50 笔记摄取 — ES 关闭（Bean 不存在）→ 返回 0 且不触达 Manager")
    void shouldReturnZero_whenNoteUpsertAndVectorUnavailable() throws Exception {
        mockMvc.perform(post(NOTE_UPSERT_URL).contentType("application/json")
                        .content("{\"noteId\":9,\"title\":\"冲咖啡入门指南\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(0));

        verifyNoInteractions(noteVectorManager);
    }

    @Test
    @DisplayName("I50 笔记摄取 — 缺 noteId → 400")
    void shouldReturn400_whenNoteUpsertMissingNoteId() throws Exception {
        mockMvc.perform(post(NOTE_UPSERT_URL).contentType("application/json")
                        .content("{\"title\":\"冲咖啡入门指南\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(noteVectorManager);
    }

    @Test
    @DisplayName("I50 笔记摄取 — 缺 title → 400")
    void shouldReturn400_whenNoteUpsertMissingTitle() throws Exception {
        mockMvc.perform(post(NOTE_UPSERT_URL).contentType("application/json")
                        .content("{\"noteId\":9}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(noteVectorManager);
    }

    @Test
    @DisplayName("I51 相似笔记 — Manager 可用 → 委托并返回命中 noteId + score")
    void shouldDelegateToManager_whenNoteSimilarAndVectorEnabled() throws Exception {
        when(noteVectorProvider.getIfAvailable()).thenReturn(noteVectorManager);
        when(noteVectorManager.similar(NOTE_ID, 5)).thenReturn(List.of(
                noteHitCO(100L, 0.9), noteHitCO(200L, 0.8)));

        mockMvc.perform(get(NOTE_SIMILAR_URL).param("note_id", String.valueOf(NOTE_ID)).param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].noteId").value(100))
                .andExpect(jsonPath("$[0].score").value(0.9))
                .andExpect(jsonPath("$[1].noteId").value(200))
                .andExpect(jsonPath("$[1].score").value(0.8))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("I51 相似笔记 — ES 关闭（Bean 不存在）→ 空列表且不触达 Manager")
    void shouldReturnEmptyList_whenNoteSimilarAndVectorUnavailable() throws Exception {
        mockMvc.perform(get(NOTE_SIMILAR_URL).param("note_id", String.valueOf(NOTE_ID)))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("[]",
                        result.getResponse().getContentAsString()));
        verifyNoInteractions(noteVectorManager);
    }

    private static SpuVectorHitCO hitCO(String spuId, double score) {
        SpuVectorHitCO co = new SpuVectorHitCO();
        co.setSpuId(spuId);
        co.setScore(score);
        return co;
    }

    private static NoteVectorHitCO noteHitCO(Long noteId, double score) {
        return new NoteVectorHitCO(noteId, score);
    }
}
