package org.icedamericanomall.domain.dto;

import java.time.LocalDateTime;

/**
 * 对话摘要 — Java Record。
 */
public record ConversationSummary(
        String conversationId,
        String agentType,
        String title,
        Integer messageCount,
        String lastMessage,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {}
