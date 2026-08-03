package org.icedamericanomall.service;

import org.icedamericanomall.domain.dto.ConversationDetail;
import org.icedamericanomall.domain.dto.ConversationSummary;

import java.util.List;

/** AI 会话管理领域服务 — V5.0 DDD 合规 */
public interface ConversationService {

    List<ConversationSummary> listConversations(Long userId);

    ConversationDetail getConversation(Long userId, String conversationId);

    void deleteConversation(Long userId, String conversationId);
}
