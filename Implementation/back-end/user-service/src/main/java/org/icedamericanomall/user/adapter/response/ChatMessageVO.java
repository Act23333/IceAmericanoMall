package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.infrastructure.persistence.chat.ChatMessageDO;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息视图对象 —— 替代原直接返回 DO 的报文（字段与重构前完全一致）。
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

    /** 持久化对象 → 视图对象。 */
    public static ChatMessageVO from(ChatMessageDO dataObject) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(dataObject.getId());
        vo.setMessageId(dataObject.getMessageId());
        vo.setConversationId(dataObject.getConversationId());
        vo.setSenderId(dataObject.getSenderId());
        vo.setSenderRole(dataObject.getSenderRole());
        vo.setReceiverId(dataObject.getReceiverId());
        vo.setContent(dataObject.getContent());
        vo.setContentType(dataObject.getContentType());
        vo.setExtra(dataObject.getExtra());
        vo.setIsRead(dataObject.getIsRead());
        vo.setIsAiGenerated(dataObject.getIsAiGenerated());
        vo.setCreateTime(dataObject.getCreateTime());
        return vo;
    }
}
