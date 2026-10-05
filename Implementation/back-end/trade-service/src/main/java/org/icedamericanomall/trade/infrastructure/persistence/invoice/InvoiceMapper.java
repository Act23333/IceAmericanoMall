package org.icedamericanomall.trade.infrastructure.persistence.invoice;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 发票单 Mapper —— 条件迁移 SQL 在此契约化（对齐 {@code AfterSaleMapper} 先例）。
 *
 * <p>状态机迁移（PRD §15.4.1）全部用显式 SQL 而非 {@code lambdaUpdate()} 链：
 * 把「WHERE 带期望状态」这一不变量落到 Mapper 契约上——可读、可单测，且不受
 * MP 代理在单元测试中不可用的限制。所有语句返回受影响行数（0 = 状态冲突/未命中）。
 */
@Mapper
public interface InvoiceMapper extends BaseMapper<InvoicePO> {

    /**
     * 开具（条件迁移 1→2）—— 回填模拟票号 + 开具时间 + 样票 objectKey（保持有效票槽位）。
     *
     * @param invoiceNo     发票申请号
     * @param mockInvoiceNo 模拟票号
     * @param pdfKey        样票 objectKey
     * @param issueTime     开具时间
     * @return 受影响行数（1=命中；0=状态冲突）
     */
    @Update("UPDATE invoice SET status = 2, issue_time = #{issueTime}, mock_invoice_no = #{mockInvoiceNo}, "
            + "pdf_key = #{pdfKey}, update_time = NOW() "
            + "WHERE invoice_no = #{invoiceNo} AND status = 1")
    int issueIfPending(@Param("invoiceNo") String invoiceNo,
                       @Param("mockInvoiceNo") String mockInvoiceNo,
                       @Param("pdfKey") String pdfKey,
                       @Param("issueTime") LocalDateTime issueTime);

    /**
     * 作废（条件迁移 1→4）—— 买家撤回 / 管理端作废共用；同时置 active_flag=NULL
     * 让出 uk_order_active 有效票槽位（终态不再占用「一单一有效票」名额）。
     *
     * @param invoiceNo 发票申请号
     * @param reason    作废原因（买家撤回传 null）
     * @return 受影响行数（1=命中；0=状态冲突）
     */
    @Update("UPDATE invoice SET status = 4, active_flag = NULL, red_reason = #{reason}, "
            + "update_time = NOW() "
            + "WHERE invoice_no = #{invoiceNo} AND status = 1")
    int cancelIfPending(@Param("invoiceNo") String invoiceNo, @Param("reason") String reason);

    /**
     * 冲红（条件迁移 2→3）—— 管理端人工冲红 / 换开前置共用（换开语义 =
     * 冲红原票 active_flag→NULL + 新票待开具 red_for_id 关联原票，PRD D5）。
     *
     * @param invoiceNo 发票申请号
     * @param reason    冲红原因
     * @return 受影响行数（1=命中；0=状态冲突/已冲红幂等空操作）
     */
    @Update("UPDATE invoice SET status = 3, active_flag = NULL, red_reason = #{reason}, "
            + "update_time = NOW() "
            + "WHERE invoice_no = #{invoiceNo} AND status = 2")
    int redFlushIfIssued(@Param("invoiceNo") String invoiceNo, @Param("reason") String reason);

    /**
     * 全额退款自动作废（条件迁移 1→4，I56 决策 D4）—— 按订单号定位有效票。
     *
     * @param orderNo 订单号
     * @param reason  系统原因码（含退款幂等键）
     * @return 受影响行数（1=命中；0=无待开具票）
     */
    @Update("UPDATE invoice SET status = 4, active_flag = NULL, red_reason = #{reason}, "
            + "update_time = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 1")
    int voidPendingByOrderNo(@Param("orderNo") String orderNo, @Param("reason") String reason);

    /**
     * 全额退款自动冲红（条件迁移 2→3，I56 决策 D4）—— 按订单号定位有效票。
     *
     * @param orderNo 订单号
     * @param reason  系统原因码（含退款幂等键）
     * @return 受影响行数（1=命中；0=无已开具票）
     */
    @Update("UPDATE invoice SET status = 3, active_flag = NULL, red_reason = #{reason}, "
            + "update_time = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 2")
    int redFlushIssuedByOrderNo(@Param("orderNo") String orderNo, @Param("reason") String reason);

    /**
     * 记录催开时间（仅待开具可催开，O10 最小实现：urge_time 持久化 + 24h 限频判定）。
     *
     * @param invoiceNo 发票申请号
     * @param urgeTime  本次催开时间
     * @return 受影响行数（1=命中；0=状态冲突）
     */
    @Update("UPDATE invoice SET urge_time = #{urgeTime}, update_time = NOW() "
            + "WHERE invoice_no = #{invoiceNo} AND status = 1")
    int markUrged(@Param("invoiceNo") String invoiceNo, @Param("urgeTime") LocalDateTime urgeTime);

    /**
     * 部分退款置标（I60，决策 D4/O6）—— 按订单号定位已开具有效票置 partial_refund_flag=1
     * （不改状态，待人工处理；不动 active_flag 有效票槽位）。
     * WHERE 限定 status=2 且未置标：已置标 / 待开具 / 终态 / 无票 → 0 行（幂等空操作语义）。
     *
     * @param orderNo 订单号
     * @return 受影响行数（1=置标成功；0=幂等空操作）
     */
    @Update("UPDATE invoice SET partial_refund_flag = 1, update_time = NOW() "
            + "WHERE order_no = #{orderNo} AND status = 2 AND partial_refund_flag = 0")
    int markPartialRefundFlagIfIssued(@Param("orderNo") String orderNo);
}
