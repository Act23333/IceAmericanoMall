package org.icedamericanomall.ai.domain.repository;

import org.icedamericanomall.ai.domain.model.conversation.ChatMessage;

import java.util.List;

/**
 * 聊天消息仓储端口（领域层）—— {@code chat_message} 聚合的持久化契约。
 *
 * <p>入参/出参均为领域对象 {@link ChatMessage}；查询一律按业务键
 * （{@code conversationId} / 参与者 {@code userId}），禁止按技术主键操作。
 *
 * <p>排序口径（由实现保证，调用方不感知 SQL）：
 * <ul>
 *   <li>{@code findByConversationAsc} / {@code findPageByConversationAsc}：按创建时间正序；</li>
 *   <li>{@code findByParticipantDesc}：按创建时间倒序（会话聚合取最近一条）。</li>
 * </ul>
 */
public interface ChatMessageRepository {

    /**
     * 新增一条消息（技术主键由数据库自增并回填到返回对象）。
     *
     * @param message 待落库的消息（id 忽略）
     * @return 落库后的消息（含回填的 id）
     */
    ChatMessage save(ChatMessage message);

    /**
     * 查询用户参与的全部消息行（发送者或接收者），按创建时间倒序。
     *
     * @param userId 用户ID
     * @return 消息列表（无数据返回空列表）
     */
    List<ChatMessage> findByParticipantDesc(Long userId);

    /**
     * 按会话分页查询消息，按创建时间正序。
     *
     * @param conversationId 会话ID
     * @param pageNum        页码（1 起）
     * @param pageSize       页大小
     * @return 当前页消息列表
     */
    List<ChatMessage> findPageByConversationAsc(String conversationId, int pageNum, int pageSize);

    /**
     * 查询会话的全部消息行（整行，含参与者字段），按创建时间正序。
     *
     * <p>用于由历史行推导会话参与者，故必须携带发送者/接收者两侧字段。
     *
     * @param conversationId 会话ID
     * @return 全部消息（无数据返回空列表）
     */
    List<ChatMessage> findByConversationAsc(String conversationId);
}
