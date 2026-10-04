package org.icedamericanomall.notification.adapter.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import org.icedamericanomall.notification.domain.model.inapp.InAppMessage;
import org.icedamericanomall.notification.domain.model.PageResult;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内信视图对象 —— 报文形状与迁移前（user-service 的 NotificationVO）保持一致，
 * 含派生字段 {@code unread}。
 */
@Data
public class InAppMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long recipientId;

    private Long senderId;

    private String senderName;

    private String senderAvatar;

    private String type;

    private String title;

    private String content;

    private String linkUrl;

    /** 是否已读：0=未读，1=已读 */
    private Integer isRead;

    private LocalDateTime createTime;

    /** 派生字段：是否未读（保持历史 JSON 的 unread 键）。 */
    public boolean isUnread() {
        return isRead == null || isRead == 0;
    }

    /**
     * 领域模型 → 视图对象（出站方向；口径来源：前端 {@code /api/notifications/**} 报文契约）。
     *
     * <p>与入库方向的 {@code InAppMessageRepositoryImpl.toModel}（基础设施层）逐字段结构相似
     * 但方向相反、归属不同层：本方法随前端报文契约变化，彼方法随 {@code notification} 表结构变化，
     * 故刻意不共用转换器。
     */
    public static InAppMessageVO from(InAppMessage message) {
        InAppMessageVO vo = new InAppMessageVO();
        vo.setId(message.id());
        vo.setRecipientId(message.recipientId());
        vo.setSenderId(message.senderId());
        vo.setSenderName(message.senderName());
        vo.setSenderAvatar(message.senderAvatar());
        vo.setType(message.type());
        vo.setTitle(message.title());
        vo.setContent(message.content());
        vo.setLinkUrl(message.linkUrl());
        vo.setIsRead(message.read() ? 1 : 0);
        vo.setCreateTime(message.createTime());
        return vo;
    }

    /**
     * 应用层领域分页 → MyBatis-Plus 分页信封（Adapter 归属）。
     *
     * <p>保持既有 {@code GET /api/notifications} HTTP 报文形状不变（{@code records/total/size/current}）；
     * 领域层 {@link PageResult} 不携带框架类型，信封形状由适配层负责。
     */
    public static IPage<InAppMessageVO> fromPage(PageResult<InAppMessage> page) {
        Page<InAppMessageVO> result = new Page<>(page.pageNum(), page.pageSize());
        result.setTotal(page.total());
        result.setRecords(page.records().stream().map(InAppMessageVO::from).toList());
        return result;
    }
}
