package org.icedamericanomall.ai.app.chat;

import org.icedamericanomall.ai.app.command.SendChatMessageCommand;
import org.icedamericanomall.ai.app.dto.ChatConversationDTO;
import org.icedamericanomall.ai.app.dto.ChatMessageDTO;
import org.icedamericanomall.ai.domain.model.conversation.ChatMessage;
import org.icedamericanomall.ai.domain.model.conversation.ConversationDetail;
import org.icedamericanomall.ai.domain.model.conversation.ConversationSummary;
import org.icedamericanomall.ai.domain.port.AgentMemoryPort;
import org.icedamericanomall.ai.domain.port.ChatMessageCache;
import org.icedamericanomall.ai.domain.repository.ChatMessageRepository;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatMessageManager 单元测试 —— 参与者校验、身份取自命令、两个 REST 面共用聚合查询。
 *
 * <p>原 {@code ConversationServiceImplTest}（会话详情/删除会话）用例已并入本类：
 * ConversationService 已合并进本 Manager（同一张表的两个 REST 面）。
 * 仓储以 {@link ChatMessageRepository} 端口打桩（PO 映射不在本层）。
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageManagerTest {

    private static final String CONVERSATION_ID = "buyer_1_seller_2_product_9";

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMessageCache chatMessageCache;

    @Mock
    private AgentMemoryPort agentMemoryPort;

    private ChatMessageManager manager;

    @BeforeEach
    void setUp() {
        manager = new ChatMessageManager(chatMessageRepository, chatMessageCache, agentMemoryPort);
    }

    @Test
    @DisplayName("读会话消息 — 非参与者 → FORBIDDEN")
    void shouldThrowForbidden_whenUserNotParticipant() {
        when(chatMessageRepository.findByConversationAsc("c-1")).thenReturn(List.of(message(1L, 2L)));

        assertThrows(ForbiddenException.class, () -> manager.assertParticipant("c-1", 99L));
    }

    @Test
    @DisplayName("读会话消息 — 参与者 → 放行")
    void shouldPass_whenUserIsParticipant() {
        when(chatMessageRepository.findByConversationAsc("c-1")).thenReturn(List.of(message(1L, 2L)));

        assertDoesNotThrow(() -> manager.assertParticipant("c-1", 2L));
    }

    @Test
    @DisplayName("读会话消息 — 会话无任何消息 → 会话不存在")
    void shouldThrowConversationNotFound_whenNoMessages() {
        when(chatMessageRepository.findByConversationAsc("c-unknown")).thenReturn(List.of());

        BizException exception = assertThrows(BizException.class,
                () -> manager.assertParticipant("c-unknown", 1L));
        assertEquals(ErrorCode.AI_CONVERSATION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("发送消息 — 发送者身份取自命令（非客户端报文），返回应用层 DTO")
    void shouldUseCommandIdentity_whenSending() {
        SendChatMessageCommand command =
                new SendChatMessageCommand("c-1", 1L, ChatPrincipal.SENDER_ROLE_BUYER, 2L, "hello", null);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saving = invocation.getArgument(0);
            saving.setId(1L);
            return saving;
        });

        ChatMessageDTO dto = manager.sendMessage(command);

        assertEquals(1L, dto.getSenderId());
        assertEquals(ChatPrincipal.SENDER_ROLE_BUYER, dto.getSenderRole());
        assertEquals(2L, dto.getReceiverId());
        assertEquals("TEXT", dto.getContentType());

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertEquals("c-1", captor.getValue().getConversationId());
        assertNotNull(captor.getValue().getMessageId(), "业务唯一标识 messageId 应由应用层生成");
        assertEquals(0, captor.getValue().getIsRead(), "新消息默认未读");
        assertNotNull(captor.getValue().getCreateTime());
        verify(chatMessageCache).incrementUnread(2L, "c-1");
    }

    @Test
    @DisplayName("IM 会话面 — 同一次聚合：取最近一条 + 累计条数 + 缓存未读数")
    void shouldAggregateConversations_whenListing() {
        LocalDateTime now = LocalDateTime.now();
        when(chatMessageRepository.findByParticipantDesc(1L)).thenReturn(List.of(
                message(CONVERSATION_ID, 1L, 2L, "hi", now),
                message("c-2", 2L, 1L, "yo", now.minusHours(1)),
                message(CONVERSATION_ID, 2L, 1L, "older", now.minusHours(2))));
        when(chatMessageCache.getUnread(1L, CONVERSATION_ID)).thenReturn(2);
        when(chatMessageCache.getUnread(1L, "c-2")).thenReturn(0);

        List<ChatConversationDTO> conversations = manager.listConversations(1L);

        assertEquals(2, conversations.size());
        ChatConversationDTO first = conversations.get(0);
        assertEquals(CONVERSATION_ID, first.getConversationId());
        assertEquals("hi", first.getLastMessage());
        assertEquals(now, first.getLastTime());
        assertEquals(2, first.getMessageCount());
        assertEquals(2, first.getUnreadCount());
        assertEquals("c-2", conversations.get(1).getConversationId());
    }

    @Test
    @DisplayName("AI 会话面 — 与 IM 面共用聚合，出参为 ConversationSummary 且不查未读")
    void shouldReuseSameAggregation_whenListingAiConversations() {
        LocalDateTime now = LocalDateTime.now();
        when(chatMessageRepository.findByParticipantDesc(1L)).thenReturn(List.of(
                message(CONVERSATION_ID, 1L, 2L, "hi", now)));

        List<ConversationSummary> summaries = manager.listAiConversations(1L);

        assertEquals(1, summaries.size());
        assertEquals(CONVERSATION_ID, summaries.get(0).conversationId());
        assertEquals("IM", summaries.get(0).agentType());
        assertEquals(1, summaries.get(0).messageCount());
        assertEquals("hi", summaries.get(0).lastMessage());
        verify(chatMessageCache, never()).getUnread(anyLong(), anyString());
    }

    @Test
    @DisplayName("会话列表 — 未登录（userId 缺失）返回空列表，两个面一致")
    void shouldReturnEmpty_whenUserIdMissing() {
        assertEquals(List.of(), manager.listConversations(null));
        assertEquals(List.of(), manager.listAiConversations(null));
    }

    @Test
    @DisplayName("会话详情 — 从 chat_message 返回真实消息")
    void shouldReturnRealMessages_whenParticipant() {
        givenMessages(message(CONVERSATION_ID, 1L, 2L, "在吗", LocalDateTime.now()));

        ConversationDetail detail = manager.getConversationDetail(1L, CONVERSATION_ID);

        assertEquals(CONVERSATION_ID, detail.conversationId());
        assertEquals(1, detail.messages().size());
        assertEquals("在吗", detail.messages().get(0).content());
        assertEquals(ChatPrincipal.SENDER_ROLE_BUYER, detail.messages().get(0).role());
    }

    @Test
    @DisplayName("会话详情 — 非参与者 → FORBIDDEN")
    void shouldThrowForbidden_whenDetailRequestedByNonParticipant() {
        givenMessages(message(CONVERSATION_ID, 1L, 2L, "在吗", LocalDateTime.now()));

        assertThrows(ForbiddenException.class, () -> manager.getConversationDetail(99L, CONVERSATION_ID));
    }

    @Test
    @DisplayName("会话详情 — 单次加载不超过详情上限 500 条")
    void shouldLoadWithDetailLimit_whenGettingDetail() {
        givenMessages(message(CONVERSATION_ID, 1L, 2L, "在吗", LocalDateTime.now()));

        manager.getConversationDetail(1L, CONVERSATION_ID);

        verify(chatMessageRepository).findPageByConversationAsc(eq(CONVERSATION_ID), eq(1), eq(500));
    }

    @Test
    @DisplayName("删除会话 — 非参与者 → FORBIDDEN，且不触碰 Agent 记忆端口")
    void shouldRejectDelete_whenNotParticipant() {
        givenMessages(message(CONVERSATION_ID, 1L, 2L, "在吗", LocalDateTime.now()));

        assertThrows(ForbiddenException.class, () -> manager.deleteConversation(99L, CONVERSATION_ID));
        verify(agentMemoryPort, never()).evictConversationMemory(anyLong(), anyString());
    }

    @Test
    @DisplayName("删除会话 — 参与者 → 经端口清理购物助手与客服两条 Agent 记忆")
    void shouldCleanAgentMemory_whenParticipantDeletes() {
        givenMessages(message(CONVERSATION_ID, 1L, 2L, "在吗", LocalDateTime.now()));

        manager.deleteConversation(1L, CONVERSATION_ID);

        verify(agentMemoryPort).evictConversationMemory(1L, CONVERSATION_ID);
    }

    /** 打桩会话详情/删除路径的分页查询结果。 */
    private void givenMessages(ChatMessage... messages) {
        when(chatMessageRepository.findPageByConversationAsc(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(messages));
    }

    private static ChatMessage message(Long senderId, Long receiverId) {
        return message("c-1", senderId, receiverId, "hi", null);
    }

    private static ChatMessage message(String conversationId, Long senderId, Long receiverId,
                                       String content, LocalDateTime createTime) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setSenderRole(ChatPrincipal.SENDER_ROLE_BUYER);
        message.setContent(content);
        message.setCreateTime(createTime);
        return message;
    }
}
