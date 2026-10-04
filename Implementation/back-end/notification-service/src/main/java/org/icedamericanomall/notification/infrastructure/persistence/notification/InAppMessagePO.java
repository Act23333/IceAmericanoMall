package org.icedamericanomall.notification.infrastructure.persistence.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内信表映射对象（PO）—— {@code notification} 表。
 *
 * <p>表所有权随站内信功能自 user-service 迁入本服务（ADR-016 收尾，表名不变，无数据迁移）。
 * 仅存在于基础设施持久化层，不外泄；未读/创建时间由仓储写入侧统一维护。
 */
@Data
@TableName("notification")
public class InAppMessagePO implements Serializable {

    @Serial
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

    private LocalDateTime createTime;
}
