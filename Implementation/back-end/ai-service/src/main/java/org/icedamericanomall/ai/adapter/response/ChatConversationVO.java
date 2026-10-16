package org.icedamericanomall.ai.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话摘要视图对象（适配层出参）—— {@code GET /api/chat/conversations} 响应。
 *
 * <p>原实现返回 {@code List<Map<String, Object>>}（弱类型，AGENTS/09-constraints 反模式），
 * 现改为强类型 VO：JSON 字段名与形状保持不变（{@code conversationId} / {@code lastMessage} /
 * {@code lastTime} / {@code unreadCount}）。
 *
 * <p>说明：05-API-Specification 的 {@code ChatConversationVO} 草案含 targetUser / spu 等更丰富字段
 * （🆕 V5.0 未实现）；按「HTTP 契约保持」纪律，本次仅做弱类型→强类型归位，不新增或改名任何字段。
 */
@Data
public class ChatConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID。 */
    private String conversationId;

    /** 最后一条消息内容。 */
    private String lastMessage;

    /** 最后一条消息时间。 */
    private LocalDateTime lastTime;

    /** 当前用户在该会话的未读数。 */
    private Integer unreadCount;
}
