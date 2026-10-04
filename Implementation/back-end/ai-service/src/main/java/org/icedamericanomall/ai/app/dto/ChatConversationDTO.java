package org.icedamericanomall.ai.app.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话摘要（应用层聚合结果）—— 两个 REST 面共用同一个聚合查询：
 *
 * <ul>
 *   <li>{@code GET /api/chat/conversations}（IM 面）→ 取 {@code conversationId/lastMessage/lastTime/unreadCount}</li>
 *   <li>{@code GET /api/ai/conversations}（AI 会话面）→ 另取 {@code messageCount} 组装 {@code ConversationSummary}</li>
 * </ul>
 *
 * <p>{@code unreadCount} 由 IM 面按需补齐（避免 AI 会话面做无用的缓存调用）；
 * {@code messageCount} 为聚合期计数，仅应用层内部使用，不外泄为响应字段。
 */
@Data
public class ChatConversationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID。 */
    private String conversationId;

    /** 最后一条消息内容。 */
    private String lastMessage;

    /** 最后一条消息时间。 */
    private LocalDateTime lastTime;

    /** 当前用户在该会话的未读数（IM 面补齐）。 */
    private Integer unreadCount;

    /** 该会话消息条数（聚合期计数，AI 会话面使用）。 */
    private Integer messageCount;
}
