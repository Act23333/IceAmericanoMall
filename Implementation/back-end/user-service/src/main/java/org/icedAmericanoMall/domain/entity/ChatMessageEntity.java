package org.icedAmericanoMall.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessageEntity {
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
