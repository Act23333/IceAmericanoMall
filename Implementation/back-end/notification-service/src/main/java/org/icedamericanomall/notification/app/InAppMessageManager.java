package org.icedamericanomall.notification.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.notification.domain.model.PageResult;
import org.icedamericanomall.notification.domain.model.inapp.InAppMessage;
import org.icedamericanomall.notification.domain.repository.InAppMessageRepository;
import org.springframework.stereotype.Component;

/**
 * 站内信用例编排器（应用层）—— 发送 / 分页 / 未读数 / 已读 / 删除。
 *
 * <p>站内信不经通道投递（短信/邮件），而是直接落 {@code notification} 表，故与
 * {@link ChannelDispatchManager} 属不同用例族，各持一个协作者：本类只依赖
 * {@link InAppMessageRepository}。
 *
 * <p>业务规则（消息自校验、self-send 忽略语义）与归属校验（recipientId 内嵌在仓储条件中）
 * 分别由领域对象与仓储承担，本类只做编排与日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppMessageManager {

    private final InAppMessageRepository inAppMessageRepository;

    /**
     * 发送站内信（落库；self-send 忽略）。
     *
     * <p>行为差异说明（ADR-016 收尾）：旧实现（user-service）在发送人昵称为空时会反查
     * seller/user 表兜底——该兜底依赖用户域数据，随边界重构移除；{@code senderName}
     * 由调用方传入，前端可按 {@code senderId} 渲染。
     *
     * @param message 站内信内容
     */
    public void sendInAppMessage(InAppMessage message) {
        if (message.senderId() != null && message.senderId().equals(message.recipientId())) {
            log.info("[notify][in-app][skip] self-send 忽略 recipientId={}", message.recipientId());
            return;
        }
        inAppMessageRepository.save(message);
        log.info("[notify][in-app][ok] recipientId={}, type={}", message.recipientId(), message.type());
    }

    /** 我的站内信分页（可按类型筛选；分页以领域 {@link PageResult} 承载）。 */
    public PageResult<InAppMessage> pageInApp(Long recipientId, String type, long pageNum, long pageSize) {
        return inAppMessageRepository.pageByRecipient(recipientId, type, pageNum, pageSize);
    }

    /** 未读计数。 */
    public int countUnread(Long recipientId) {
        return inAppMessageRepository.countUnread(recipientId);
    }

    /** 标记单条已读（归属校验在仓储条件内；通知不存在或不属于本人 → NOTIFICATION_NOT_FOUND）。 */
    public void markRead(Long id, Long recipientId) {
        if (!inAppMessageRepository.markRead(id, recipientId)) {
            throw new BizException(ErrorCode.NOTIFICATION_NOT_FOUND, "通知不存在");
        }
    }

    /** 全部已读。 */
    public void markAllRead(Long recipientId) {
        inAppMessageRepository.markAllRead(recipientId);
    }

    /** 删除站内信（归属校验在仓储条件内）。 */
    public void deleteInApp(Long id, Long recipientId) {
        inAppMessageRepository.deleteByIdAndRecipient(id, recipientId);
    }
}
