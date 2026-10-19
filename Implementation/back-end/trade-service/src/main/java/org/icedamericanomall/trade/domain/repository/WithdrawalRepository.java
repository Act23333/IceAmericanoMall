package org.icedamericanomall.trade.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.trade.domain.model.finance.Withdrawal;

/**
 * 商家提现单仓储（领域端口）—— 实现方：{@code infrastructure.persistence.finance.WithdrawalRepositoryImpl}。
 */
public interface WithdrawalRepository {

    /** 按主键取提现单（不存在返回 null）。 */
    Withdrawal findById(Long id);

    /** 保存提现单（id 回填）。 */
    Withdrawal save(Withdrawal withdrawal);

    /**
     * 审核提现：条件迁移「待审核 → 目标状态」并记录审核备注（<b>不读-改-写</b>）。
     *
     * <p>0 行 = 该单已不是待审核（重复审核 / 并发双击）—— 幂等 no-op。调用方必须先拿到 1 行
     * 才去动资金账户，否则同一笔打款会被记两遍。
     *
     * @return 影响行数：1=本次真的完成审核；0=状态已不是待审核
     */
    /**
     * 受理出款：待审核 → 付款中 + 落幂等键。
     *
     * @param id       提现单主键
     * @param payoutNo 出款幂等键（重试必须复用同一个）
     * @return 影响行数；0 = 已受理或已终态（幂等）
     */
    int claimPaying(Long id, String payoutNo);

    /**
     * 付款中 → 已打款（拿到渠道回执后才允许）。
     *
     * @param id 提现单主键
     * @return 影响行数；0 = 状态不符，调用方不得记账
     */
    int markPaidIfPaying(Long id);

    /**
     * 取「付款中」的单主键（供出款任务扫描）。
     *
     * @param limit 上限
     * @return 主键列表
     */
    java.util.List<Long> findPayingIds(int limit);

    int reviewIfPending(Long id, Integer fromStatus, Integer toStatus, String adminRemark);

    /** 商家提现单分页（创建时间倒序）。 */
    IPage<Withdrawal> pageBySeller(Long sellerId, int page, int size);

    /** 管理端提现单分页（可按状态过滤；创建时间倒序）。 */
    IPage<Withdrawal> pageByStatus(Integer status, int page, int size);
}
