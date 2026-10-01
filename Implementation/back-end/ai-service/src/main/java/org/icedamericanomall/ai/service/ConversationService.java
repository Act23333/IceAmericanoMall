package org.icedamericanomall.ai.service;

import org.icedamericanomall.ai.domain.dto.ConversationDetail;
import org.icedamericanomall.ai.domain.dto.ConversationSummary;

import java.util.List;

/** AI 会话管理领域服务 — V5.0 DDD 合规 */
public interface ConversationService {

    List<ConversationSummary> listConversations(Long userId);

    ConversationDetail getConversation(Long userId, String conversationId);

    void deleteConversation(Long userId, String conversationId);
}
