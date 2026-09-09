package org.icedamericanomall.domain.dto;

import java.util.List;

/**
 * 对话详情 — Java Record。
 */
public record ConversationDetail(
        String conversationId,
        String agentType,
        List<MessageInfo> messages
) {}
