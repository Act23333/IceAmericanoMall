package org.icedamericanomall.ai.domain.model.conversation;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息（会话聚合实体，纯 POJO，零框架依赖）—— {@code chat_message} 表的领域投影。
 *
 * <p>归属与语义：
 * <ul>
 *   <li>一条消息即一次买卖家（或买家-客服）会话通信，按 {@code conversationId} 归组；</li>
 *   <li>发送者身份由应用层保证取自 STOMP 会话 Principal（非客户端报文），参与者
 *       判定规则见 {@link ConversationAccessPolicy}；</li>
 *   <li>持久化经 {@code ChatMessageRepository} 端口（入参/出参均为本领域对象），
 *       PO 映射细节收敛在基础设施层。</li>
 * </ul>
 */
@Data
public class ChatMessage {

    /** 技术主键（落库后由仓储回填；领域内不作为操作键）。 */
    private Long id;

    /** 业务唯一标识（UUID，应用层生成）。 */
    private String messageId;

    /** 会话ID（买卖家会话的业务聚合键）。 */
    private String conversationId;

    /** 发送者用户ID。 */
    private Long senderId;

    /** 发送者角色（SELLER / BUYER 契约值，见 {@code ChatPrincipal}）。 */
    private String senderRole;

    /** 接收者用户ID。 */
    private Long receiverId;

    /** 消息内容。 */
    private String content;

    /** 内容类型（TEXT 等契约值）。 */
    private String contentType;

    /** 扩展字段（JSON 文本，可空）。 */
    private String extra;

    /** 已读标记：0=未读。 */
    private Integer isRead;

    /** 是否 AI 生成：1=是。 */
    private Integer isAiGenerated;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
