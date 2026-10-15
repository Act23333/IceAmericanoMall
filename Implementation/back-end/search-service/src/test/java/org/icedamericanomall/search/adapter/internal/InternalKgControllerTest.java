package org.icedamericanomall.search.adapter.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.dto.KgEdgeUpsertCO;
import org.icedamericanomall.dto.KgNeighborCO;
import org.icedamericanomall.search.app.KgEdgeManager;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalKgController 端点测试 —— 委托、条件装配降级（I57=0 / I58=[] / I59 空操作）与入参校验。
 *
 * <p>Mock app 层与 {@link ObjectProvider}（ES 关闭时 Manager Bean 不存在的口径），
 * standalone MockMvc 不起 Spring 上下文；注册 ia-common {@link GlobalExceptionHandler}
 * 验证 ADR-004 内部接口裸状态码纪律（400 + {"code","msg"}）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalKgControllerTest {

    private static final String UPSERT_URL = "/internal/search/kg/edge/upsert";
    private static final String NEIGHBORS_URL = "/internal/search/kg/neighbors";
    private static final String DELETE_URL = "/internal/search/kg/edges";
    private static final Long SRC_ID = 9L;

    @Mock
    private KgEdgeManager kgEdgeManager;

    @Mock
    private ObjectProvider<KgEdgeManager> kgEdgeManagerProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalKgController(kgEdgeManagerProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("I57 摄取 — Manager 可用 → 委托并返回写入边数（Req→CO 字段对齐）")
    void shouldDelegateToManager_whenUpsertAndEsEnabled() throws Exception {
        when(kgEdgeManagerProvider.getIfAvailable()).thenReturn(kgEdgeManager);
        when(kgEdgeManager.upsertEdges(any(KgEdgeUpsertCO.class))).thenReturn(2);

        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("relType", "BELONGS_TO");
        edge.put("dstType", "category");
        edge.put("dstId", 10);
        edge.put("weight", 0);
        edge.put("source", "DIRECT");
        edge.put("status", 1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("srcType", "product");
        body.put("srcId", SRC_ID);
        body.put("edges", List.of(edge));

        mockMvc.perform(post(UPSERT_URL).contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(2));

        ArgumentCaptor<KgEdgeUpsertCO> captor = ArgumentCaptor.forClass(KgEdgeUpsertCO.class);
        verify(kgEdgeManager).upsertEdges(captor.capture());
        assertEquals("product", captor.getValue().getSrcType());
        assertEquals(SRC_ID, captor.getValue().getSrcId());
        assertEquals(1, captor.getValue().getEdges().size());
        assertEquals("BELONGS_TO", captor.getValue().getEdges().get(0).getRelType());
        assertEquals("category", captor.getValue().getEdges().get(0).getDstType());
        assertEquals(10L, captor.getValue().getEdges().get(0).getDstId());
        assertEquals("DIRECT", captor.getValue().getEdges().get(0).getSource());
    }

    @Test
    @DisplayName("I57 摄取 — ES 关闭（Bean 不存在）→ 返回 0 且不触达 Manager")
    void shouldReturnZero_whenUpsertAndEsDisabled() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("srcType", "product");
        body.put("srcId", SRC_ID);
        body.put("edges", List.of());

        mockMvc.perform(post(UPSERT_URL).contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(0));

        verifyNoInteractions(kgEdgeManager);
    }

    @Test
    @DisplayName("I57 摄取 — 缺 srcType → 400")
    void shouldReturn400_whenUpsertMissingSrcType() throws Exception {
        mockMvc.perform(post(UPSERT_URL).contentType("application/json")
                        .content("{\"srcId\":9,\"edges\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(kgEdgeManager);
    }

    @Test
    @DisplayName("I57 摄取 — 缺 edges → 400")
    void shouldReturn400_whenUpsertMissingEdges() throws Exception {
        mockMvc.perform(post(UPSERT_URL).contentType("application/json")
                        .content("{\"srcType\":\"product\",\"srcId\":9}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(kgEdgeManager);
    }

    @Test
    @DisplayName("I58 邻居 — Manager 可用 → 委托并返回 dst/relType/weight/score")
    void shouldDelegateToManager_whenNeighborsAndEsEnabled() throws Exception {
        when(kgEdgeManagerProvider.getIfAvailable()).thenReturn(kgEdgeManager);
        when(kgEdgeManager.neighbors("product", SRC_ID, null, null, 1, null))
                .thenReturn(List.of(new KgNeighborCO("category", 10L, "BELONGS_TO", 0, 1.5F)));

        mockMvc.perform(get(NEIGHBORS_URL)
                        .param("src_type", "product").param("src_id", String.valueOf(SRC_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dstType").value("category"))
                .andExpect(jsonPath("$[0].dstId").value(10))
                .andExpect(jsonPath("$[0].relType").value("BELONGS_TO"))
                .andExpect(jsonPath("$[0].weight").value(0))
                .andExpect(jsonPath("$[0].score").value(1.5))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("I58 邻居 — ES 关闭（Bean 不存在）→ 空列表且不触达 Manager")
    void shouldReturnEmptyList_whenNeighborsAndEsDisabled() throws Exception {
        mockMvc.perform(get(NEIGHBORS_URL)
                        .param("src_type", "product").param("src_id", String.valueOf(SRC_ID)))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("[]", result.getResponse().getContentAsString()));
        verifyNoInteractions(kgEdgeManager);
    }

    @Test
    @DisplayName("I58 邻居 — depth>2 → 400 PARAM_ERROR（ADR-004 内部接口裸状态码）")
    void shouldReturn400_whenDepthOverMax() throws Exception {
        when(kgEdgeManagerProvider.getIfAvailable()).thenReturn(kgEdgeManager);
        when(kgEdgeManager.neighbors(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BadRequestException(ErrorCode.PARAM_ERROR, "depth 超出能力边界"));

        mockMvc.perform(get(NEIGHBORS_URL)
                        .param("src_type", "product").param("src_id", String.valueOf(SRC_ID))
                        .param("depth", "3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("I59 清边 — Manager 可用 → 委托 deleteEdges")
    void shouldDelegateToManager_whenDeleteAndEsEnabled() throws Exception {
        when(kgEdgeManagerProvider.getIfAvailable()).thenReturn(kgEdgeManager);

        mockMvc.perform(delete(DELETE_URL)
                        .param("src_type", "product").param("src_id", String.valueOf(SRC_ID)))
                .andExpect(status().isOk());

        verify(kgEdgeManager).deleteEdges("product", SRC_ID);
    }

    @Test
    @DisplayName("I59 清边 — ES 关闭（Bean 不存在）→ 空操作且不触达 Manager")
    void shouldNoOp_whenDeleteAndEsDisabled() throws Exception {
        mockMvc.perform(delete(DELETE_URL)
                        .param("src_type", "product").param("src_id", String.valueOf(SRC_ID)))
                .andExpect(status().isOk());

        verifyNoInteractions(kgEdgeManager);
    }
}
