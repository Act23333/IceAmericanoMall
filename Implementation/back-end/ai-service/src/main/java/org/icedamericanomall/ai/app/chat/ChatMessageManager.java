package org.icedamericanomall.ai.app.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.app.command.SendChatMessageCommand;
import org.icedamericanomall.ai.app.dto.ChatConversationDTO;
import org.icedamericanomall.ai.app.dto.ChatMessageDTO;
import org.icedamericanomall.ai.domain.model.conversation.ChatMessage;
import org.icedamericanomall.ai.domain.model.conversation.ConversationDetail;
import org.icedamericanomall.ai.domain.model.conversation.ConversationId;
import org.icedamericanomall.ai.domain.model.conversation.ConversationSummary;
import org.icedamericanomall.ai.domain.model.conversation.MessageInfo;
import org.icedamericanomall.ai.domain.port.AgentMemoryPort;
import org.icedamericanomall.ai.domain.port.ChatMessageCache;
import org.icedamericanomall.ai.domain.repository.ChatMessageRepository;
import org.icedamericanomall.ai.domain.service.ConversationAccessPolicy;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.icedamericanomall.common.utils.IdGenerator;

/**
 * 买家-商家会话（应用层）—— 客服域的唯一会话入口（人工 IM 面 + AI 会话面）。
 *
 * <p>事实来源唯一：{@code chat_message} 落库的行。两个 REST 面共享同一个聚合查询
 * {@link #aggregateConversations(Long)}，各自组装不同出参：
 *
 * <ul>
 *   <li>{@code GET /api/chat/conversations}（IM 面）→ {@code List<ChatConversationVO>}（含未读数）</li>
 *   <li>{@code GET /api/ai/conversations}（AI 会话面）→ {@code List<ConversationSummary>}</li>
 * </ul>
 *
 * <p><b>合并说明</b>：原 {@code service/ConversationService(Impl)} 与本类查的是同一张表、
 * 同一批行（仅出参形状不同），已合并至本类以消除重复查询与遗留 {@code service/} 包；
 * 会话详情的参与者校验、删除会话时的 Agent 记忆清理随之收敛到此。
 *
 * <p>持久化经 {@link ChatMessageRepository} 端口（PO 映射收敛在仓储实现）；
 * 热数据（最近消息 / 未读计数）在 {@link ChatMessageCache} 端口之后（Redis 实现）；
 * 会话记忆清理经 {@link AgentMemoryPort} 端口（Redis 实现删除购物助手 + 客服两条记忆）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageManager {

    /** 未读标记（客户端未读） */
    private static final int UNREAD = 0;

    /** 默认内容类型 */
    private static final String CONTENT_TYPE_TEXT = "TEXT";

    /** 会话类型：买卖家人工会话（chat_message 承载的会话形态）。 */
    private static final String AGENT_TYPE_IM = "IM";

    /** 详情接口单次返回的消息上限。 */
    private static final int DETAIL_MESSAGE_LIMIT = 500;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageCache chatMessageCache;
    private final AgentMemoryPort agentMemoryPort;

    /**
     * 发送消息：落库 → 缓存最近列表 → 未读计数 +1。
     *
     * <p><b>会话键归一（ADR-063 §决策 7 表 #4，批次 B7b）</b>：入参 {@code conversationId} 在
     * <b>本方法</b>统一过 {@link ConversationId#normalize(String)} —— 这是全服务<b>唯一的派生点</b>：
     * 旧形态 57 字符复合串在这里被重算成 32 位内容派生值（{@code VARCHAR(36)} 装得下），
     * 已归一的值幂等通过。绝不在适配层或前端再拼一套（两套映射 = 两处可漂移的编码表）。
     *
     * @param command 发送命令（发送者身份已由 STOMP 会话解析，非客户端报文）
     * @return 落库后的消息（应用层 DTO，供适配层转 STOMP 广播负载）
     */
    public ChatMessageDTO sendMessage(SendChatMessageCommand command) {
        String conversationId = ConversationId.normalize(command.conversationId());
        ChatMessage message = new ChatMessage();
        message.setMessageId(IdGenerator.nextId());
        message.setConversationId(conversationId);
        message.setSenderId(command.senderId());
        message.setSenderRole(command.senderRole());
        message.setReceiverId(command.receiverId());
        message.setContent(command.content());
        message.setContentType(command.contentType() == null ? CONTENT_TYPE_TEXT : command.contentType());
        message.setIsRead(UNREAD);
        message.setCreateTime(LocalDateTime.now());
        ChatMessage saved = chatMessageRepository.save(message);

        ChatMessageDTO dto = toDTO(saved);
        chatMessageCache.cacheRecent(conversationId, dto);
        chatMessageCache.incrementUnread(command.receiverId(), conversationId);
        return dto;
    }

    /**
     * 校验当前用户是该会话的参与者（参与者由 chat_message 历史行推导）。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @throws BizException       会话不存在（无任何消息）
     * @throws ForbiddenException 非会话参与者
     */
    public void assertParticipant(String conversationId, Long userId) {
        Set<Long> participants = loadParticipantIds(conversationId);
        if (participants.isEmpty()) {
            throw new BizException(ErrorCode.AI_CONVERSATION_NOT_FOUND);
        }
        if (!ConversationAccessPolicy.isParticipant(participants, userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "非会话参与者，无权访问该会话");
        }
    }

    /**
     * IM 会话列表（去重取最近一条 + 未读数）—— {@code GET /api/chat/conversations}。
     *
     * @param userId 当前用户ID（null 返回空列表）
     * @return 按最后消息时间倒序的会话摘要（未读数由缓存补齐）
     */
    public List<ChatConversationDTO> listConversations(Long userId) {
        List<ChatConversationDTO> conversations = aggregateConversations(userId);
        for (ChatConversationDTO conversation : conversations) {
            conversation.setUnreadCount(chatMessageCache.getUnread(userId, conversation.getConversationId()));
        }
        return conversations;
    }

    /**
     * AI 会话列表 —— {@code GET /api/ai/conversations}。
     *
     * <p>与 IM 面共用 {@link #aggregateConversations(Long)}；此处不查未读数（出参无该字段）。
     *
     * @param userId 当前用户ID（null 返回空列表）
     * @return 会话摘要列表（按最后消息时间倒序）
     */
    public List<ConversationSummary> listAiConversations(Long userId) {
        return aggregateConversations(userId).stream()
                .map(conversation -> new ConversationSummary(
                        conversation.getConversationId(), AGENT_TYPE_IM, conversation.getConversationId(),
                        conversation.getMessageCount(), conversation.getLastMessage(),
                        conversation.getLastTime(), conversation.getLastTime()))
                .toList();
    }

    /** 会话历史消息（分页）。 */
    public List<ChatMessageDTO> getMessages(String conversationId, int pageNum, int size) {
        return chatMessageRepository.findPageByConversationAsc(conversationId, pageNum, size)
                .stream()
                .map(ChatMessageManager::toDTO)
                .toList();
    }

    /** 用户未读总数。 */
    public int getUnreadCount(Long userId) {
        return chatMessageCache.totalUnread(userId);
    }

    /**
     * 会话详情（含历史消息）。
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID
     * @return 会话详情（消息按时间正序）
     * @throws BizException       会话不存在（无任何消息）
     * @throws ForbiddenException 非会话参与者
     */
    public ConversationDetail getConversationDetail(Long userId, String conversationId) {
        List<ChatMessage> rows = loadMessages(conversationId);
        assertParticipant(rows, userId);
        List<MessageInfo> messages = rows.stream().map(ChatMessageManager::toMessageInfo).toList();
        return new ConversationDetail(conversationId, AGENT_TYPE_IM, messages);
    }

    /**
     * 删除会话（清理该会话的 Agent 记忆：购物助手 + 客服，经 {@link AgentMemoryPort}）。
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID
     * @throws BizException       会话不存在（无任何消息）
     * @throws ForbiddenException 非会话参与者
     */
    public void deleteConversation(Long userId, String conversationId) {
        assertParticipant(loadMessages(conversationId), userId);
        agentMemoryPort.evictConversationMemory(userId, conversationId);
        log.debug("AI 会话记忆已删除: userId={}, conversationId={}", userId, conversationId);
    }

    /**
     * 会话聚合（唯一查询入口）—— 按时间倒序取当前用户参与的全部消息行，
     * 按会话去重：首行即最近一条（消息内容 + 时间），并累计消息条数。
     *
     * @param userId 当前用户ID（null 返回空列表）
     * @return 按最后消息时间倒序的会话聚合结果
     */
    private List<ChatConversationDTO> aggregateConversations(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<ChatMessage> rows = chatMessageRepository.findByParticipantDesc(userId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, ChatConversationDTO> byConversation = new LinkedHashMap<>();
        for (ChatMessage row : rows) {
            ChatConversationDTO conversation = byConversation.get(row.getConversationId());
            if (conversation == null) {
                conversation = new ChatConversationDTO();
                conversation.setConversationId(row.getConversationId());
                conversation.setLastMessage(row.getContent());
                conversation.setLastTime(row.getCreateTime());
                conversation.setMessageCount(0);
                byConversation.put(row.getConversationId(), conversation);
            }
            conversation.setMessageCount(conversation.getMessageCount() + 1);
        }
        return new ArrayList<>(byConversation.values());
    }

    /** 按时间正序加载会话消息（上限 {@link #DETAIL_MESSAGE_LIMIT} 条）。 */
    private List<ChatMessage> loadMessages(String conversationId) {
        return chatMessageRepository.findPageByConversationAsc(conversationId, 1, DETAIL_MESSAGE_LIMIT);
    }

    /** 非参与者一律拒绝；会话不存在（无消息）按不存在处理。 */
    private static void assertParticipant(List<ChatMessage> rows, Long userId) {
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.AI_CONVERSATION_NOT_FOUND);
        }
        Set<Long> participantIds = new HashSet<>();
        for (ChatMessage row : rows) {
            participantIds.add(row.getSenderId());
            participantIds.add(row.getReceiverId());
        }
        if (!ConversationAccessPolicy.isParticipant(participantIds, userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "非会话参与者，无权访问该会话");
        }
    }

    /** 单条消息 → 应用层 DTO。 */
    private static ChatMessageDTO toDTO(ChatMessage row) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(row.getId());
        dto.setMessageId(row.getMessageId());
        dto.setConversationId(row.getConversationId());
        dto.setSenderId(row.getSenderId());
        dto.setSenderRole(row.getSenderRole());
        dto.setReceiverId(row.getReceiverId());
        dto.setContent(row.getContent());
        dto.setContentType(row.getContentType());
        dto.setExtra(row.getExtra());
        dto.setIsRead(row.getIsRead());
        dto.setIsAiGenerated(row.getIsAiGenerated());
        dto.setCreateTime(row.getCreateTime());
        return dto;
    }

    /** 单条消息 → 会话详情中的消息项（仅对外所需字段）。 */
    private static MessageInfo toMessageInfo(ChatMessage row) {
        return new MessageInfo(row.getSenderRole(), row.getContent(), row.getCreateTime());
    }

    /** 由会话消息推导参与者集合（发送者 ∪ 接收者）。 */
    private Set<Long> loadParticipantIds(String conversationId) {
        List<ChatMessage> rows = chatMessageRepository.findByConversationAsc(conversationId);
        Set<Long> participantIds = new HashSet<>();
        for (ChatMessage row : rows) {
            if (row.getSenderId() != null) {
                participantIds.add(row.getSenderId());
            }
            if (row.getReceiverId() != null) {
                participantIds.add(row.getReceiverId());
            }
        }
        return participantIds;
    }
}
