package org.icedamericanomall.user.infrastructure.persistence.notification;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * {@code notification} 表映射对象（DO）—— 仅存在于基础设施持久化层，不外泄。
 *
 * <p>响应形状由 {@code adapter/response/NotificationVO} 承担（含派生的 unread 字段，
 * 与历史 JSON 报文保持一致）。
 */
@Data
@TableName("notification")
public class NotificationDO implements Serializable {

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

    /** 是否已读：0=未读，1=已读 */
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
