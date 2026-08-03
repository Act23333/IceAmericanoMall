package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("notification")
public class NotificationEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收者 */
    private Long recipientId;

    /** 发送者 (NULL=系统消息) */
    private Long senderId;

    /** 发送者名称: 用户名或店铺名 */
    private String senderName;

    /** 发送者头像 */
    private String senderAvatar;

    /** 类型: LIKE/REPLY/APPEND/FOLLOW/ORDER/SYSTEM */
    private String type;

    /** 标题 */
    private String title;

    /** 摘要 */
    private String content;

    /** 跳转链接 */
    private String linkUrl;

    /** 0=未读, 1=已读 */
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
