package org.icedamericanomall.trade.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;

import java.time.LocalDateTime;

/**
 * 发票单仓储（领域端口）—— 实现方：{@code infrastructure.persistence.invoice.InvoiceRepositoryImpl}。
 *
 * <p>状态机迁移全部走「条件更新」（WHERE 带期望状态，PRD §15.4.1 迁移语义）：
 * 受影响行数 0 = 状态冲突 / 已被并发处理，调用方按幂等或拒绝收敛。
 * 迁入终态（已冲红/已作废）的条件更新同时置 {@code active_flag=NULL} 让出
 * {@code uk_order_active} 有效票槽位，保证「一单一有效票」不变量。
 */
public interface InvoiceRepository {

    /** 保存发票申请（id 回填）。 */
    Invoice save(Invoice invoice);

    /** 按发票申请号取发票（不存在返回 null）。 */
    Invoice findByInvoiceNo(String invoiceNo);

    /** 按订单号取最近一张发票（任意状态，换开/重开的 {@code red_for_id} 关联依据）。 */
    Invoice findLatestByOrderNo(String orderNo);

    /** 按订单号取当前有效发票（待开具/已开具，active_flag=1；无有效票返回 null）。 */
    Invoice findActiveByOrderNo(String orderNo);

    /** 买家发票分页（创建时间倒序，可按状态筛选）。 */
    IPage<Invoice> pageByBuyer(Long userId, Integer status, int page, int size);

    /**
     * 管理端发票分页（可按状态/商家/部分退款标记筛选；创建时间倒序）。
     *
     * @param urgeOnly true=仅看有催开记录的发票并按 urge_time 倒序（O10 催开排序视图）
     */
    IPage<Invoice> pageForAdmin(Integer status, Long sellerId, Boolean partialRefund,
                                boolean urgeOnly, int page, int size);

    /**
     * 开具（条件迁移 1→2）—— 回填模拟票号 + 开具时间 + 样票 objectKey。
     *
     * @return 受影响行数（1=命中；0=状态冲突）
     */
    int issueIfPending(String invoiceNo, String mockInvoiceNo, String pdfKey, LocalDateTime issueTime);

    /**
     * 作废（条件迁移 1→4）—— 买家撤回 / 管理端作废共用；同时让出有效票槽位。
     *
     * @param reason 作废原因（买家撤回传 null）
     * @return 受影响行数（1=命中；0=状态冲突）
     */
    int cancelIfPending(String invoiceNo, String reason);

    /**
     * 冲红（条件迁移 2→3）—— 管理端人工冲红；同时让出有效票槽位。
     *
     * @param reason 冲红原因（人工必填）
     * @return 受影响行数（1=命中；0=状态冲突）
     */
    int redFlushIfIssued(String invoiceNo, String reason);

    /**
     * 换开前置冲红（条件迁移 2→3）—— 原票冲红并失效（active_flag→NULL），
     * 与新票创建（应用层同一本地事务）共同实现「换开 = 冲红重开」（PRD D5，守卫清单 #3）。
     *
     * @param reason 冲红原因（换开留痕）
     * @return 受影响行数（1=命中；0=状态冲突）
     */
    int markRedAndExpire(String invoiceNo, String reason);

    /**
     * 全额退款自动联动（I56，决策 D4）—— 按订单号处理有效票：
     * 待开具(1)→自动作废(4)、已开具(2)→自动冲红(3)，原因 = 系统原因码；
     * 无有效票 / 已终态 = 幂等空操作。内部两条条件更新同事务。
     *
     * @param orderNo      订单号
     * @param systemReason 系统原因码（含退款幂等键审计追溯）
     * @return 实际迁移结果（0=无有效票幂等空操作；3=已自动冲红；4=已自动作废）
     */
    int voidOnFullRefund(String orderNo, String systemReason);

    /**
     * 记录催开时间（条件更新：仅待开具可催开，O10）。
     *
     * @return 受影响行数（1=命中；0=状态冲突）
     */
    int markUrged(String invoiceNo, LocalDateTime urgeTime);

    /**
     * 部分退款置标（I60，决策 D4/O6）—— 该订单已开具有效票置 {@code partial_refund_flag=1}
     * （不改状态、待人工处理）。条件更新自带幂等：已置标 / 待开具（遇部分退款无需标记，
     * 开具后人工冲红口径不受影响）/ 终态 / 无票 → 0 行幂等空操作。
     *
     * @param orderNo 订单号
     * @return 受影响行数（1=置标成功；0=幂等空操作）
     */
    int markPartialRefundFlagIfIssued(String orderNo);
}
