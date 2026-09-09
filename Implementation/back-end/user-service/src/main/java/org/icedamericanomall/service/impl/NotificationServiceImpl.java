package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.domain.entity.NotificationEntity;
import org.icedamericanomall.domain.entity.SellerEntity;
import org.icedamericanomall.domain.entity.UserEntity;
import org.icedamericanomall.mapper.NotificationMapper;
import org.icedamericanomall.mapper.SellerMapper;
import org.icedamericanomall.mapper.UserMapper;
import org.icedamericanomall.service.NotificationService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知服务 — 文档标准分层 (Controller→Manager→Service→Mapper)
 */
@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, NotificationEntity>
        implements NotificationService {

    private final UserMapper userMapper;
    private final SellerMapper sellerMapper;

    public NotificationServiceImpl(UserMapper userMapper, SellerMapper sellerMapper) {
        this.userMapper = userMapper;
        this.sellerMapper = sellerMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void send(Long recipientId, Long senderId, String senderName, String senderAvatar,
                     String type, String title, String content, String linkUrl) {
        if (recipientId.equals(senderId)) return;
        if (senderName == null || senderName.isEmpty()) {
            senderName = resolveSenderName(senderId);
        }
        NotificationEntity n = new NotificationEntity();
        n.setRecipientId(recipientId);
        n.setSenderId(senderId);
        n.setSenderName(senderName);
        n.setSenderAvatar(senderAvatar);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setLinkUrl(linkUrl);
        n.setIsRead(0);
        save(n);
    }

    @Override
    public IPage<NotificationEntity> pageByUser(Long userId, String type, int page, int size) {
        var w = new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getRecipientId, userId)
                .orderByDesc(NotificationEntity::getCreateTime);
        if (type != null && !type.isEmpty()) w.eq(NotificationEntity::getType, type);
        return page(new Page<>(page, size), w);
    }

    @Override public int countUnread(Long userId) { return baseMapper.countUnread(userId); }

    @Override
    public void markRead(Long id, Long userId) {
        NotificationEntity n = getById(id);
        if (n == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "通知不存在");
        n.markRead();
        baseMapper.markRead(id, userId);
    }

    @Override public void markAllRead(Long userId) { baseMapper.markAllRead(userId); }

    @Override
    public void deleteById(Long id, Long userId) {
        lambdaUpdate().eq(NotificationEntity::getId, id)
                .eq(NotificationEntity::getRecipientId, userId).remove();
    }

    private String resolveSenderName(Long senderId) {
        if (senderId == null) return "系统";
        try {
            SellerEntity seller = sellerMapper.selectOne(
                    new LambdaQueryWrapper<SellerEntity>().eq(SellerEntity::getUserId, senderId));
            if (seller != null) return seller.getShopName();
        } catch (Exception ignored) {}
        try {
            UserEntity user = userMapper.selectById(senderId);
            if (user != null && user.getUsername() != null) return user.getUsername();
        } catch (Exception ignored) {}
        return "用户" + senderId;
    }
}
