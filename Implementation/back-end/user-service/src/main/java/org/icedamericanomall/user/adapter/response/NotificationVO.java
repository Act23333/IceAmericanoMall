package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.infrastructure.persistence.notification.NotificationDO;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息通知视图对象 —— 替代原直接返回 DO 的报文（字段与派生 unread 与重构前完全一致）。
 */
@Data
public class NotificationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long recipientId;

    private Long senderId;

    private String senderName;

    private String senderAvatar;

    private String type;

    private String title;

    private String content;

    private String linkUrl;

    /** 是否已读：0=未读，1=已读 */
    private Integer isRead;

    private LocalDateTime createTime;

    /** 派生字段：是否未读（保持历史 JSON 的 unread 键）。 */
    public boolean isUnread() {
        return isRead == null || isRead == 0;
    }

    /** 持久化对象 → 视图对象。 */
    public static NotificationVO from(NotificationDO dataObject) {
        NotificationVO vo = new NotificationVO();
        vo.setId(dataObject.getId());
        vo.setRecipientId(dataObject.getRecipientId());
        vo.setSenderId(dataObject.getSenderId());
        vo.setSenderName(dataObject.getSenderName());
        vo.setSenderAvatar(dataObject.getSenderAvatar());
        vo.setType(dataObject.getType());
        vo.setTitle(dataObject.getTitle());
        vo.setContent(dataObject.getContent());
        vo.setLinkUrl(dataObject.getLinkUrl());
        vo.setIsRead(dataObject.getIsRead());
        vo.setCreateTime(dataObject.getCreateTime());
        return vo;
    }
}
