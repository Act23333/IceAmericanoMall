package org.icedamericanomall.trade.domain.repository;

import org.icedamericanomall.trade.domain.model.invoice.InvoiceTitle;

import java.util.List;

/**
 * 常用发票抬头仓储（领域端口）—— 实现方：{@code infrastructure.persistence.invoice.InvoiceTitleRepositoryImpl}。
 *
 * <p>默认抬头唯一性：{@code uk_user_default(user_id, is_default)} 多 NULL 特性兜底
 * （一用户至多一条默认）；换设默认前必须先 {@link #clearDefaultFlag} 清旧默认。
 * 软删（@TableLogic）由实现方承载，删除可恢复。
 */
public interface InvoiceTitleRepository {

    /** 按抬头 id 取抬头（不过滤归属——INV8 归属校验需区分「不存在 1103 / 非本人 403」，由应用层强校验）；不存在返回 null。 */
    InvoiceTitle findById(Long id);

    /** 按抬头 id + 归属用户取抬头（不存在或非本人返回 null，INV1 titleId 引用入口）。 */
    InvoiceTitle findByIdAndUserId(Long id, Long userId);

    /** 保存抬头（id 回填）。 */
    InvoiceTitle save(InvoiceTitle title);

    /** 全量更新抬头（增改税号/抬头/默认标记等）。 */
    void update(InvoiceTitle title);

    /**
     * 全量更新抬头（显式 SQL：税号三字段与 is_default 允许置 NULL 清列——
     * MP {@code updateById} 忽略空值，不满足「企业转个人清税号 / 取消默认」语义）。
     *
     * @param title 已通过聚合守卫的抬头（id + userId 必填，WHERE 双条件防越权）
     * @return 受影响行数（0=不存在 / 非本人 / 已被并发软删）
     */
    int updateFull(InvoiceTitle title);

    /** 软删抬头（仅限本人抬头）。 */
    void deleteByIdAndUserId(Long id, Long userId);

    /** 本人抬头列表（创建时间倒序）。 */
    List<InvoiceTitle> listByUserId(Long userId);

    /**
     * 清除本人默认抬头标记（is_default→NULL）—— 换设默认的前置步，
     * 保证 {@code uk_user_default} 唯一语义下至多一条默认。
     *
     * @return 受影响行数
     */
    int clearDefaultFlag(Long userId);
}
