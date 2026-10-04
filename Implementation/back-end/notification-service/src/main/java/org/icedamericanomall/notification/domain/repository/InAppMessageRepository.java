package org.icedamericanomall.notification.domain.repository;

import org.icedamericanomall.notification.domain.model.inapp.InAppMessage;
import org.icedamericanomall.notification.domain.model.PageResult;

/**
 * 站内信仓储（领域层端口，基础设施层实现，落 {@code notification} 表）。
 *
 * <p>分页以领域内的 {@link PageResult}（纯 JDK）承载，端口不出现 MyBatis-Plus 类型；
 * 基础设施实现负责与 {@code IPage} 互转。归属校验（recipientId）内嵌在更新/删除条件中，
 * 防止越权操作他人通知。
 */
public interface InAppMessageRepository {

    /** 保存一条站内信（写入时置未读与创建时间）。 */
    void save(InAppMessage message);

    /** 按接收者分页查询（可按类型过滤，创建时间倒序）。 */
    PageResult<InAppMessage> pageByRecipient(Long recipientId, String type, long pageNum, long pageSize);

    /** 未读计数。 */
    int countUnread(Long recipientId);

    /** 标记单条已读（带归属校验），返回是否命中。 */
    boolean markRead(Long id, Long recipientId);

    /** 全部已读，返回命中条数。 */
    int markAllRead(Long recipientId);

    /** 删除（带归属校验），返回是否命中。 */
    boolean deleteByIdAndRecipient(Long id, Long recipientId);
}
