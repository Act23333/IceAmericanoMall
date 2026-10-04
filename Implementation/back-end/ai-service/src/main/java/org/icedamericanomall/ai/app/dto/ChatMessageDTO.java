package org.icedamericanomall.ai.app.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话消息（应用层传输对象）。
 *
 * <p>由持久化对象在应用层装配，适配层经 {@code ChatMessageConvertor} 转为
 * {@code ChatMessageVO} 出参（应用层不返回 VO，见系统规范「应用层禁止返回 VO」）。
 *
 * <p>字段与迁移前 STOMP 广播/REST 报文逐字一致，故转换后 JSON 形状不变。
 */
@Data
public class ChatMessageDTO implements Serializable {

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
