package org.icedamericanomall.ai.domain.service;

import java.util.Set;

/**
 * 会话访问策略（领域服务，纯规则，无技术依赖）。
 *
 * <p>会话参与者集合由 chat_message 表的历史行推导（发送者 ∪ 接收者），
 * 只有参与者本人可以读取该会话的消息，防止越权读取他人会话。
 */
public final class ConversationAccessPolicy {

    private ConversationAccessPolicy() {
    }

    /**
     * 判定用户是否属于该会话的参与者集合。
     *
     * @param participantIds 参与者用户ID集合（由会话消息推导）
     * @param userId         当前用户ID
     * @return true 表示有权访问
     */
    public static boolean isParticipant(Set<Long> participantIds, Long userId) {
        return userId != null && participantIds != null && participantIds.contains(userId);
    }
}
