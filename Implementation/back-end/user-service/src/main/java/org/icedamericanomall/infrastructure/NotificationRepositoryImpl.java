package org.icedamericanomall.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.domain.entity.NotificationEntity;
import org.icedamericanomall.domain.repository.NotificationRepository;
import org.icedamericanomall.mapper.NotificationMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 通知仓储 MyBatis 实现 — Infrastructure 层
 */
@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationMapper notificationMapper;

    public NotificationRepositoryImpl(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Override
    public void save(NotificationEntity n) {
        notificationMapper.insert(n);
    }

    @Override
    public Optional<NotificationEntity> findById(Long id) {
        return Optional.ofNullable(notificationMapper.selectById(id));
    }

    @Override
    public IPage<NotificationEntity> pageByUser(Long userId, String type, int page, int size) {
        var w = new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getRecipientId, userId)
                .orderByDesc(NotificationEntity::getCreateTime);
        if (type != null && !type.isEmpty()) w.eq(NotificationEntity::getType, type);
        return notificationMapper.selectPage(new Page<>(page, size), w);
    }

    @Override public int countUnread(Long userId) { return notificationMapper.countUnread(userId); }

    @Override public int markRead(Long id, Long userId) { return notificationMapper.markRead(id, userId); }

    @Override public int markAllRead(Long userId) { return notificationMapper.markAllRead(userId); }

    @Override
    public int deleteByIdAndUser(Long id, Long userId) {
        return notificationMapper.delete(
                new LambdaQueryWrapper<NotificationEntity>()
                        .eq(NotificationEntity::getId, id)
                        .eq(NotificationEntity::getRecipientId, userId));
    }
}
