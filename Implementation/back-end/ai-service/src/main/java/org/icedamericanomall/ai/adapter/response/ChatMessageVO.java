package org.icedamericanomall.ai.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息视图对象 —— 适配层出参（REST 历史消息与 STOMP 广播负载共用）。
 *
 * <p>字段与迁移前完全一致（JSON 形状不变）；装配由 {@code adapter/assembler/ChatMessageConvertor}
 * 从应用层 {@code ChatMessageDTO} 完成，VO 不再直接依赖持久化对象。
 */
@Data
public class ChatMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String messageId;

    private String conversationId;

    private Long senderId;

    private String senderRole;

    private Long receiverId;

    private String content;

    private String contentType;

    private String extra;

    private Integer isRead;

    private Integer isAiGenerated;

    private LocalDateTime createTime;
}
