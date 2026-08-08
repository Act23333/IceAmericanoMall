package org.icedamericanomall.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.domain.entity.NotificationEntity;

import java.util.Optional;

/**
 * 通知仓储接口 — Domain 层定义契约
 */
public interface NotificationRepository {

    void save(NotificationEntity notification);

    Optional<NotificationEntity> findById(Long id);

    IPage<NotificationEntity> pageByUser(Long userId, String type, int page, int size);

    int countUnread(Long userId);

    int markRead(Long id, Long userId);

    int markAllRead(Long userId);

    int deleteByIdAndUser(Long id, Long userId);
}
