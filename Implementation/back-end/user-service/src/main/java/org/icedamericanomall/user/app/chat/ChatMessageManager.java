package org.icedamericanomall.user.app.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.user.adapter.response.ChatMessageVO;
import org.icedamericanomall.user.app.port.ChatMessageCache;
import org.icedamericanomall.user.infrastructure.persistence.chat.ChatMessageDO;
import org.icedamericanomall.user.infrastructure.persistence.chat.ChatMessageMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 买家-商家会话消息（应用层）—— 发送（落库 + 缓存）、会话列表、历史消息、未读统计。
 *
 * <p>热数据（最近消息 / 未读计数）在 {@link ChatMessageCache} 端口之后（Redis 实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageManager {

    /** 未读标记（客户端未读） */
    private static final int UNREAD = 0;

    /** 默认内容类型 */
    private static final String CONTENT_TYPE_TEXT = "TEXT";

    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageCache chatMessageCache;

    /**
     * 发送消息：落库 → 缓存最近列表 → 未读计数 +1。
     *
     * @return 落库后的消息视图（供 STOMP 广播）
     */
    public ChatMessageVO sendMessage(String conversationId, Long senderId, String senderRole,
                                     Long receiverId, String content, String contentType) {
        ChatMessageDO entity = new ChatMessageDO();
        entity.setMessageId(UUID.randomUUID().toString());
        entity.setConversationId(conversationId);
        entity.setSenderId(senderId);
        entity.setSenderRole(senderRole);
        entity.setReceiverId(receiverId);
        entity.setContent(content);
        entity.setContentType(contentType == null ? CONTENT_TYPE_TEXT : contentType);
        entity.setIsRead(UNREAD);
        entity.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(entity);

        ChatMessageVO vo = ChatMessageVO.from(entity);
        chatMessageCache.cacheRecent(conversationId, vo);
        chatMessageCache.incrementUnread(receiverId, conversationId);
        return vo;
    }

    /** 会话列表（去重取最近一条 + 未读数）。 */
    public List<Map<String, Object>> listConversations(Long userId) {
        List<ChatMessageDO> list = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageDO>()
                        .and(w -> w.eq(ChatMessageDO::getSenderId, userId)
                                .or().eq(ChatMessageDO::getReceiverId, userId))
                        .orderByDesc(ChatMessageDO::getCreateTime));
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessageDO m : list) {
            if (!seen.add(m.getConversationId())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("conversationId", m.getConversationId());
            item.put("lastMessage", m.getContent());
            item.put("lastTime", m.getCreateTime());
            item.put("unreadCount", chatMessageCache.getUnread(userId, m.getConversationId()));
            result.add(item);
        }
        return result;
    }

    /** 会话历史消息（分页）。 */
    public List<ChatMessageVO> getMessages(String conversationId, int pageNum, int size) {
        return chatMessageMapper.selectPage(new Page<>(pageNum, size),
                        new LambdaQueryWrapper<ChatMessageDO>()
                                .eq(ChatMessageDO::getConversationId, conversationId)
                                .orderByAsc(ChatMessageDO::getCreateTime))
                .getRecords().stream()
                .map(ChatMessageVO::from)
                .toList();
    }

    /** 用户未读总数。 */
    public int getUnreadCount(Long userId) {
        return chatMessageCache.totalUnread(userId);
    }
}
