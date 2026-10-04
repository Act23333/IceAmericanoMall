package org.icedamericanomall.notification.infrastructure.persistence.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.notification.domain.model.inapp.InAppMessage;
import org.icedamericanomall.notification.domain.model.PageResult;
import org.icedamericanomall.notification.domain.repository.InAppMessageRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 站内信仓储实现（基础设施层）—— 落 {@code notification} 表。
 *
 * <p>归属校验（recipientId）内嵌在更新/删除条件中，防止越权操作他人通知。
 */
@Component
@RequiredArgsConstructor
public class InAppMessageRepositoryImpl implements InAppMessageRepository {

    /** 未读 */
    private static final int UNREAD = 0;
    /** 已读 */
    private static final int READ = 1;

    private final InAppMessageMapper mapper;

    @Override
    public void save(InAppMessage message) {
        InAppMessagePO po = new InAppMessagePO();
        po.setRecipientId(message.recipientId());
        po.setSenderId(message.senderId());
        po.setSenderName(message.senderName());
        po.setSenderAvatar(message.senderAvatar());
        po.setType(message.type());
        po.setTitle(message.title());
        po.setContent(message.content());
        po.setLinkUrl(message.linkUrl());
        po.setIsRead(UNREAD);
        po.setCreateTime(LocalDateTime.now());
        mapper.insert(po);
    }

    @Override
    public PageResult<InAppMessage> pageByRecipient(Long recipientId, String type, long pageNum, long pageSize) {
        LambdaQueryWrapper<InAppMessagePO> wrapper = new LambdaQueryWrapper<InAppMessagePO>()
                .eq(InAppMessagePO::getRecipientId, recipientId)
                .orderByDesc(InAppMessagePO::getCreateTime);
        if (type != null && !type.isBlank()) {
            wrapper.eq(InAppMessagePO::getType, type);
        }
        IPage<InAppMessage> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper)
                .convert(InAppMessageRepositoryImpl::toModel);
        // 基础设施边界：MyBatis-Plus IPage → 领域分页值对象（domain 包零框架依赖）
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public int countUnread(Long recipientId) {
        return Math.toIntExact(mapper.selectCount(new LambdaQueryWrapper<InAppMessagePO>()
                .eq(InAppMessagePO::getRecipientId, recipientId)
                .eq(InAppMessagePO::getIsRead, UNREAD)));
    }

    @Override
    public boolean markRead(Long id, Long recipientId) {
        return mapper.update(null, new LambdaUpdateWrapper<InAppMessagePO>()
                .set(InAppMessagePO::getIsRead, READ)
                .eq(InAppMessagePO::getId, id)
                .eq(InAppMessagePO::getRecipientId, recipientId)) > 0;
    }

    @Override
    public int markAllRead(Long recipientId) {
        return mapper.update(null, new LambdaUpdateWrapper<InAppMessagePO>()
                .set(InAppMessagePO::getIsRead, READ)
                .eq(InAppMessagePO::getRecipientId, recipientId)
                .eq(InAppMessagePO::getIsRead, UNREAD));
    }

    @Override
    public boolean deleteByIdAndRecipient(Long id, Long recipientId) {
        return mapper.delete(new LambdaQueryWrapper<InAppMessagePO>()
                .eq(InAppMessagePO::getId, id)
                .eq(InAppMessagePO::getRecipientId, recipientId)) > 0;
    }

    /**
     * PO → 领域实体（入站方向；口径来源 {@code notification} 表列定义）。
     *
     * <p>与出站方向的 {@code InAppMessageVO.from}（适配层）逐字段结构相似但方向相反、
     * 归属不同层：本方法属基础设施层的入库映射，改动随表结构；VO 属适配层的出站报文，
     * 改动随前端契约。二者刻意不共用转换器，避免表结构与前端报文互相牵制。
     */
    private static InAppMessage toModel(InAppMessagePO po) {
        return new InAppMessage(po.getId(), po.getRecipientId(), po.getSenderId(),
                po.getSenderName(), po.getSenderAvatar(), po.getType(), po.getTitle(),
                po.getContent(), po.getLinkUrl(),
                po.getIsRead() != null && po.getIsRead() == 1, po.getCreateTime());
    }
}
