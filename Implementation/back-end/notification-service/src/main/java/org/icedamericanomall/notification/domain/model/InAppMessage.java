package org.icedamericanomall.notification.domain.model;

/**
 * 站内信内容（领域值对象）—— <b>契约预留，通道尚未实现</b>。
 *
 * <p>站内信需要落库、未读数、已读回执等能力（V5.0 规划），当前只保留领域模型与
 * {@code NotificationDispatcher} 的显式拒绝规则，避免调用方误以为通知已送达。
 *
 * @param recipientId 接收者用户 ID
 * @param senderId    触发者用户 ID（系统通知可为空）
 * @param senderName  触发者昵称
 * @param type        通知类型（REPLY / LIKE / SYSTEM ...）
 * @param title       标题
 * @param content     正文
 * @param linkUrl     跳转链接
 */
public record InAppMessage(Long recipientId,
                           Long senderId,
                           String senderName,
                           String type,
                           String title,
                           String content,
                           String linkUrl) {

    /** 紧凑构造器：接收者与类型必填。 */
    public InAppMessage {
        if (recipientId == null) {
            throw new IllegalArgumentException("站内信接收者不能为空");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("站内信类型不能为空");
        }
    }

    /** 通道标识。 */
    public String channelCode() {
        return "IN_APP";
    }
}
