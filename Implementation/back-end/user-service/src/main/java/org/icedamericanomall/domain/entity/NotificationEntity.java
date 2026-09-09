package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知实体 — COLA 充血模型 V5.0
 */
@Getter
@TableName("notification")
public class NotificationEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipientId;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String type;
    private String title;
    private String content;
    private String linkUrl;
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ==================== 领域行为 ====================

    /** 标记已读 */
    public void markRead() {
        this.isRead = 1;
    }

    /** 是否未读 */
    public boolean isUnread() {
        return this.isRead == null || this.isRead == 0;
    }

    // ==================== 持久层 setter ====================

    public void setId(Long v) { this.id = v; }
    public void setRecipientId(Long v) { this.recipientId = v; }
    public void setSenderId(Long v) { this.senderId = v; }
    public void setSenderName(String v) { this.senderName = v; }
    public void setSenderAvatar(String v) { this.senderAvatar = v; }
    public void setType(String v) { this.type = v; }
    public void setTitle(String v) { this.title = v; }
    public void setContent(String v) { this.content = v; }
    public void setLinkUrl(String v) { this.linkUrl = v; }
    public void setIsRead(Integer v) { this.isRead = v; }
    public void setCreateTime(LocalDateTime v) { this.createTime = v; }
}
