package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.domain.entity.NotificationEntity;
import org.icedamericanomall.domain.entity.SellerEntity;
import org.icedamericanomall.domain.entity.UserEntity;
import org.icedamericanomall.domain.repository.NotificationRepository;
import org.icedamericanomall.mapper.NotificationMapper;
import org.icedamericanomall.mapper.SellerMapper;
import org.icedamericanomall.mapper.UserMapper;
import org.icedamericanomall.service.NotificationService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知应用服务 — COLA DDD V5.0
 *
 * Application 层: 编排业务流程, @Transactional 在此。
 * Domain 逻辑: NotificationEntity.markRead() 充血模型。
 * Infrastructure: NotificationRepository 负责持久化。
 * 保留 ServiceImpl 继承以保证 MyBatis-Plus IService 兼容。
 */
@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, NotificationEntity>
        implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserMapper userMapper;
    private final SellerMapper sellerMapper;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                    UserMapper userMapper, SellerMapper sellerMapper) {
        this.notificationRepository = notificationRepository;
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
        notificationRepository.save(n);
    }

    @Override
    public IPage<NotificationEntity> pageByUser(Long userId, String type, int page, int size) {
        return notificationRepository.pageByUser(userId, type, page, size);
    }

    @Override
    public int countUnread(Long userId) {
        return notificationRepository.countUnread(userId);
    }

    @Override
    public void markRead(Long id, Long userId) {
        NotificationEntity n = notificationRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "通知不存在"));
        n.markRead(); // 充血模型
        notificationRepository.markRead(id, userId);
    }

    @Override
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    @Override
    public void deleteById(Long id, Long userId) {
        notificationRepository.deleteByIdAndUser(id, userId);
    }

    /** 解析发送者名称: 商家→店铺名, 用户→用户名 */
    private String resolveSenderName(Long senderId) {
        if (senderId == null) return "系统";
        try {
            SellerEntity seller = sellerMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SellerEntity>()
                            .eq(SellerEntity::getUserId, senderId));
            if (seller != null) return seller.getShopName();
        } catch (Exception ignored) {}
        try {
            UserEntity user = userMapper.selectById(senderId);
            if (user != null && user.getUsername() != null) return user.getUsername();
        } catch (Exception ignored) {}
        return "用户" + senderId;
    }
}
