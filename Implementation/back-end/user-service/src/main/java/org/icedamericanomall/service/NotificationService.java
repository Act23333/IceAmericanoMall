package org.icedamericanomall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.domain.entity.NotificationEntity;

public interface NotificationService extends IService<NotificationEntity> {

    /** 发送通知 */
    void send(Long recipientId, Long senderId, String senderName, String senderAvatar,
              String type, String title, String content, String linkUrl);

    /** 用户通知列表 (分页) */
    IPage<NotificationEntity> pageByUser(Long userId, String type, int page, int size);

    /** 未读计数 */
    int countUnread(Long userId);

    /** 标记已读 */
    void markRead(Long id, Long userId);

    /** 全部已读 */
    void markAllRead(Long userId);

    /** 删除通知 */
    void deleteById(Long id, Long userId);
}
