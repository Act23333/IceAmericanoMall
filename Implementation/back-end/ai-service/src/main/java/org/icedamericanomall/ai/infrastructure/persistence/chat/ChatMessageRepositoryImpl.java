package org.icedamericanomall.ai.infrastructure.persistence.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.ai.domain.model.conversation.ChatMessage;
import org.icedamericanomall.ai.domain.repository.ChatMessageRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 聊天消息仓储实现（基础设施层）—— MyBatis-Plus 落库，PO ↔ 领域对象互转内联。
 *
 * <p>查询口径与端口契约一致：会话内按创建时间正序、参与者维度倒序；
 * 参与者查询不裁剪列（Lambda 列裁剪在条件构造期就要查 MyBatis-Plus 的 lambda 缓存，
 * 取整行后由调用方在内存聚合）。
 */
@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private final ChatMessageMapper chatMessageMapper;

    @Override
    public ChatMessage save(ChatMessage message) {
        ChatMessagePO po = toPO(message);
        chatMessageMapper.insert(po);
        return toDomain(po);
    }

    @Override
    public List<ChatMessage> findByParticipantDesc(Long userId) {
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessagePO>()
                        .and(w -> w.eq(ChatMessagePO::getSenderId, userId)
                                .or().eq(ChatMessagePO::getReceiverId, userId))
                        .orderByDesc(ChatMessagePO::getCreateTime))
                .stream()
                .map(ChatMessageRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public List<ChatMessage> findPageByConversationAsc(String conversationId, int pageNum, int pageSize) {
        return chatMessageMapper.selectPage(new Page<>(pageNum, pageSize),
                        new LambdaQueryWrapper<ChatMessagePO>()
                                .eq(ChatMessagePO::getConversationId, conversationId)
                                .orderByAsc(ChatMessagePO::getCreateTime))
                .getRecords()
                .stream()
                .map(ChatMessageRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public List<ChatMessage> findByConversationAsc(String conversationId) {
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessagePO>()
                        .eq(ChatMessagePO::getConversationId, conversationId)
                        .orderByAsc(ChatMessagePO::getCreateTime))
                .stream()
                .map(ChatMessageRepositoryImpl::toDomain)
                .toList();
    }

    /** 领域对象 → PO。 */
    private static ChatMessagePO toPO(ChatMessage message) {
        ChatMessagePO po = new ChatMessagePO();
        po.setId(message.getId());
        po.setMessageId(message.getMessageId());
        po.setConversationId(message.getConversationId());
        po.setSenderId(message.getSenderId());
        po.setSenderRole(message.getSenderRole());
        po.setReceiverId(message.getReceiverId());
        po.setContent(message.getContent());
        po.setContentType(message.getContentType());
        po.setExtra(message.getExtra());
        po.setIsRead(message.getIsRead());
        po.setIsAiGenerated(message.getIsAiGenerated());
        po.setCreateTime(message.getCreateTime());
        return po;
    }

    /** PO → 领域对象。 */
    private static ChatMessage toDomain(ChatMessagePO po) {
        ChatMessage message = new ChatMessage();
        message.setId(po.getId());
        message.setMessageId(po.getMessageId());
        message.setConversationId(po.getConversationId());
        message.setSenderId(po.getSenderId());
        message.setSenderRole(po.getSenderRole());
        message.setReceiverId(po.getReceiverId());
        message.setContent(po.getContent());
        message.setContentType(po.getContentType());
        message.setExtra(po.getExtra());
        message.setIsRead(po.getIsRead());
        message.setIsAiGenerated(po.getIsAiGenerated());
        message.setCreateTime(po.getCreateTime());
        return message;
    }
}
