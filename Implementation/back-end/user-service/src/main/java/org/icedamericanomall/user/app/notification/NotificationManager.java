package org.icedamericanomall.user.app.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.adapter.response.NotificationVO;
import org.icedamericanomall.user.domain.repository.SellerRepository;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.icedamericanomall.user.infrastructure.persistence.notification.NotificationDO;
import org.icedamericanomall.user.infrastructure.persistence.notification.NotificationMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息通知（应用层）—— 站内信发送（内部端点 / 关注通知）、按用户分页、已读状态维护。
 *
 * <p>发送人昵称兜底：优先店铺名（商家），其次用户名，最后"用户{id}"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationManager {

    /** 未读 */
    private static final int UNREAD = 0;

    private final NotificationMapper notificationMapper;
    private final SellerRepository sellerRepository;
    private final UserRepository userRepository;

    /** 发送通知（self-send 忽略；空发送人名自动兜底）。 */
    @Transactional(rollbackFor = Exception.class)
    public void send(Long recipientId, Long senderId, String senderName, String senderAvatar,
                     String type, String title, String content, String linkUrl) {
        if (recipientId.equals(senderId)) {
            return;
        }
        if (senderName == null || senderName.isEmpty()) {
            senderName = resolveSenderName(senderId);
        }
        NotificationDO n = new NotificationDO();
        n.setRecipientId(recipientId);
        n.setSenderId(senderId);
        n.setSenderName(senderName);
        n.setSenderAvatar(senderAvatar);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setLinkUrl(linkUrl);
        n.setIsRead(UNREAD);
        notificationMapper.insert(n);
    }

    /** 用户通知列表（分页，可按类型筛选）。 */
    public IPage<NotificationVO> pageByUser(Long userId, String type, int pageNum, int size) {
        LambdaQueryWrapper<NotificationDO> wrapper = new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getRecipientId, userId)
                .orderByDesc(NotificationDO::getCreateTime);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(NotificationDO::getType, type);
        }
        IPage<NotificationDO> result = notificationMapper.selectPage(new Page<>(pageNum, size), wrapper);
        return result.convert(NotificationVO::from);
    }

    /** 未读计数。 */
    public int countUnread(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    /** 标记单条已读（归属校验在 SQL WHERE 内兜底）。 */
    public void markRead(Long id, Long userId) {
        NotificationDO n = notificationMapper.selectById(id);
        if (n == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "通知不存在");
        }
        notificationMapper.markRead(id, userId);
    }

    /** 全部已读。 */
    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId);
    }

    /** 删除通知（归属校验在 SQL WHERE 内兜底）。 */
    public void deleteById(Long id, Long userId) {
        notificationMapper.delete(new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getId, id)
                .eq(NotificationDO::getRecipientId, userId));
    }

    // ==================== 内部方法 ====================

    private String resolveSenderName(Long senderId) {
        if (senderId == null) {
            return "系统";
        }
        try {
            String shopName = sellerRepository.findByUserId(senderId)
                    .map(s -> s.getShopName()).orElse(null);
            if (shopName != null) {
                return shopName;
            }
        } catch (Exception ignored) {
            // 兜底到用户名
        }
        try {
            String username = userRepository.findById(senderId)
                    .map(u -> u.getUsername()).orElse(null);
            if (username != null) {
                return username;
            }
        } catch (Exception ignored) {
            // 兜底到默认名
        }
        return "用户" + senderId;
    }
}
