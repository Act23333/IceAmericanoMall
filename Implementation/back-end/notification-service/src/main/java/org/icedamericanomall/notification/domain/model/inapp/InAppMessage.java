package org.icedamericanomall.notification.domain.model.inapp;

import java.time.LocalDateTime;

/**
 * 站内信（领域实体）—— 已落库实现（ADR-016 收尾）：发送、未读数、已读回执、删除。
 *
 * <p>新建时 id / createTime 为空，由仓储层生成；相等性按 id。
 *
 * @param id           技术主键（新建时为空）
 * @param recipientId  接收者用户 ID
 * @param senderId     触发者用户 ID（系统通知可为空）
 * @param senderName   触发者昵称（可为空：由调用方传入，或前端按 senderId 渲染）
 * @param senderAvatar 触发者头像（可为空）
 * @param type         通知类型（REPLY / LIKE / SYSTEM / ORDER ...）
 * @param title        标题
 * @param content      正文
 * @param linkUrl      跳转链接（前端路由，可为空）
 * @param read         是否已读
 * @param createTime   创建时间
 */
public record InAppMessage(Long id,
                           Long recipientId,
                           Long senderId,
                           String senderName,
                           String senderAvatar,
                           String type,
                           String title,
                           String content,
                           String linkUrl,
                           boolean read,
                           LocalDateTime createTime) {

    /** 紧凑构造器：接收者与类型必填。 */
    public InAppMessage {
        if (recipientId == null) {
            throw new IllegalArgumentException("站内信接收者不能为空");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("站内信类型不能为空");
        }
    }

    /** 新建一条未读站内信（发送入口）。 */
    public static InAppMessage unread(Long recipientId, Long senderId, String senderName,
                                      String senderAvatar, String type, String title,
                                      String content, String linkUrl) {
        return new InAppMessage(null, recipientId, senderId, senderName, senderAvatar,
                type, title, content, linkUrl, false, null);
    }
}
