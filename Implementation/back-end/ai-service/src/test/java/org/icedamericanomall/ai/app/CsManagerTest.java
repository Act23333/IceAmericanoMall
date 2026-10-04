package org.icedamericanomall.ai.app;

import dev.langchain4j.model.chat.ChatModel;
import org.icedamericanomall.ai.app.dto.AiChatRequest;
import org.icedamericanomall.ai.app.dto.AiChatResponse;
import org.icedamericanomall.ai.domain.service.ContentSafetyFilter;
import org.icedamericanomall.ai.infrastructure.agent.CustomerServiceAssistant;
import org.icedamericanomall.ai.infrastructure.config.AiProperties;
import org.icedamericanomall.ai.infrastructure.rag.RAGRetriever;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CsManager 编排单元测试 —— Mock Port（Agent / 安全过滤器 / 摘要模型 / RAG 检索器）。
 * 转人工判定（置信度启发式）已并入 CsManager，测试走真实启发式逻辑。
 */
@ExtendWith(MockitoExtension.class)
class CsManagerTest {

    @Mock
    private AiProperties aiProperties;

    @Mock
    private ContentSafetyFilter safetyFilter;

    @Mock
    private ChatModel chatModel;

    @Mock
    private CustomerServiceAssistant customerServiceAssistant;

    @Mock
    private RAGRetriever ragRetriever;

    private CsManager csManager;

    @BeforeEach
    void setUp() {
        csManager = new CsManager(aiProperties, safetyFilter, chatModel,
                customerServiceAssistant, null, ragRetriever);
    }

    @Test
    @DisplayName("内容安全拦截 → 抛 AI_CONTENT_FILTERED")
    void shouldThrowContentFiltered_whenInputRejected() {
        when(safetyFilter.checkInput("违规内容")).thenReturn("输入包含敏感词汇");

        BizException exception = assertThrows(BizException.class,
                () -> csManager.chat(new AiChatRequest("违规内容", null, null), 1L));

        assertEquals(ErrorCode.AI_CONTENT_FILTERED, exception.getErrorCode());
        verifyNoInteractions(customerServiceAssistant);
    }

    @Test
    @DisplayName("Agent 回答低置信度 → 转人工文案替换原回答")
    void shouldHandoffToHuman_whenConfidenceIsLow() {
        when(safetyFilter.checkInput("怎么退货")).thenReturn(null);
        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getRag()).thenReturn(ragDisabled());
        when(customerServiceAssistant.chat(anyString(), eq("怎么退货"))).thenReturn("抱歉，我不确定");
        when(chatModel.chat(anyString())).thenReturn("用户询问退货流程");

        AiChatResponse response = csManager.chat(new AiChatRequest("怎么退货", "conv-1", null), 7L);

        assertTrue(response.reply().startsWith("【转人工】"), "低置信度回答应替换为转人工文案");
        assertTrue(response.reply().contains("AI初步回复: 抱歉，我不确定"), "转人工文案应携带原回答");
        assertTrue(response.reply().contains("对话摘要: 用户询问退货流程"), "转人工文案应携带 LLM 摘要");
        assertEquals("conv-1", response.conversationId());
    }

    @Test
    @DisplayName("AI 未启用 → 兜底文案且不调用 Agent")
    void shouldReturnUnavailableMessage_whenAiDisabled() {
        when(safetyFilter.checkInput("你好")).thenReturn(null);
        when(aiProperties.isEnabled()).thenReturn(false);

        AiChatResponse response = csManager.chat(new AiChatRequest("你好", null, null), null);

        assertEquals(csManager.unavailableMessage(), response.reply());
        verifyNoInteractions(customerServiceAssistant, chatModel);
    }

    @Test
    @DisplayName("会话ID 缺失 → 服务端生成，memoryId 按 cs:{userId}:{conversationId} 组装")
    void shouldBuildMemoryIdWithAnonymous_whenNotLoggedIn() {
        assertEquals("cs:7:conv-1", csManager.memoryId(7L, "conv-1"));
        assertEquals("cs:anonymous:conv-1", csManager.memoryId(null, "conv-1"));
    }

    @Test
    @DisplayName("RAG 开启 + 带 sellerId → 检索命中拼进 prompt 后送 Agent")
    void shouldPrependKnowledgeContext_whenRagEnabledAndSellerPresent() {
        when(safetyFilter.checkInput("怎么退货")).thenReturn(null);
        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getRag()).thenReturn(ragEnabled(5));
        when(ragRetriever.retrieveForSeller("怎么退货", 99L))
                .thenReturn("以下是与用户问题相关的商家知识：\n1. 退货政策\n7天无理由\n");
        when(customerServiceAssistant.chat(anyString(), anyString())).thenReturn("7天无理由退货");

        csManager.chat(new AiChatRequest("怎么退货", "conv-1", 99L), 7L);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(customerServiceAssistant).chat(anyString(), promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("7天无理由"), "prompt 应包含检索到的知识内容");
        assertTrue(prompt.contains("用户问题：怎么退货"), "prompt 应包含原始用户问题");
    }

    @Test
    @DisplayName("RAG 开启但检索为空（null）→ 使用原始问题，无 RAG 直接应答")
    void shouldFallBackToRawMessage_whenRetrievalEmpty() {
        when(safetyFilter.checkInput("你好")).thenReturn(null);
        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getRag()).thenReturn(ragEnabled(5));
        when(ragRetriever.retrieveForSeller("你好", 99L)).thenReturn(null);
        when(customerServiceAssistant.chat(anyString(), eq("你好"))).thenReturn("您好");

        csManager.chat(new AiChatRequest("你好", "conv-1", 99L), 7L);

        verify(customerServiceAssistant).chat(anyString(), eq("你好"));
    }

    @Test
    @DisplayName("RAG 开启但检索抛异常 → 降级为原始问题，不阻断应答")
    void shouldDegradeToRawMessage_whenRetrievalThrows() {
        when(safetyFilter.checkInput("你好")).thenReturn(null);
        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getRag()).thenReturn(ragEnabled(5));
        when(ragRetriever.retrieveForSeller("你好", 99L))
                .thenThrow(new IllegalStateException("embedding down"));
        when(customerServiceAssistant.chat(anyString(), eq("你好"))).thenReturn("您好");

        AiChatResponse response = csManager.chat(new AiChatRequest("你好", "conv-1", 99L), 7L);

        assertEquals("您好", response.reply());
        verify(customerServiceAssistant).chat(anyString(), eq("你好"));
    }

    @Test
    @DisplayName("RAG 关闭 → 检索器零触达（开关关闭=行为与现状一致）")
    void shouldNotTouchRetriever_whenRagDisabled() {
        when(safetyFilter.checkInput("你好")).thenReturn(null);
        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getRag()).thenReturn(ragDisabled());
        when(customerServiceAssistant.chat(anyString(), eq("你好"))).thenReturn("您好");

        csManager.chat(new AiChatRequest("你好", "conv-1", 99L), 7L);

        verify(ragRetriever, never()).retrieveForSeller(anyString(), anyLong());
        verify(customerServiceAssistant).chat(anyString(), eq("你好"));
    }

    private static AiProperties.Rag ragEnabled(int topK) {
        AiProperties.Rag rag = new AiProperties.Rag();
        rag.setEnabled(true);
        rag.setTopK(topK);
        return rag;
    }

    private static AiProperties.Rag ragDisabled() {
        AiProperties.Rag rag = new AiProperties.Rag();
        rag.setEnabled(false);
        return rag;
    }
}
