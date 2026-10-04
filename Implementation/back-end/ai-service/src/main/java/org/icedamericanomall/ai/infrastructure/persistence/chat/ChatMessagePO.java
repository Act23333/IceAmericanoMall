package org.icedamericanomall.ai.infrastructure.persistence.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天消息表映射对象（PO）—— {@code chat_message} 表（表所有权随客服域迁入本服务）。
 */
@Data
@TableName("chat_message")
public class ChatMessagePO {
    @TableId(type = IdType.AUTO)
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
