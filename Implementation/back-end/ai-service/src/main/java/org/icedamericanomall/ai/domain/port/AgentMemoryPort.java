package org.icedamericanomall.ai.domain.port;

/**
 * AI Agent 会话记忆端口（领域层）—— 会话删除时的记忆清理能力。
 *
 * <p>语义：清理指定用户在指定会话下关联的<b>全部</b> AI Agent 记忆
 * （购物助手 + 智能客服）。记忆存储介质与键布局由实现封装，领域/应用层不感知。
 *
 * <p>能力缺失（如 AI 未启用、记忆本就不存在）时实现应静默幂等，
 * 不得因清理失败阻断会话删除主流程。
 */
public interface AgentMemoryPort {

    /**
     * 清理用户在指定会话下的全部 Agent 记忆。
     *
     * @param userId         用户ID
     * @param conversationId 会话ID
     */
    void evictConversationMemory(Long userId, String conversationId);
}
