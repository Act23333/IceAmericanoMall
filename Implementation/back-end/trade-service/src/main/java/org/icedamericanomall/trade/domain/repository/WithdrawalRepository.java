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
    int reviewIfPending(Long id, Integer fromStatus, Integer toStatus, String adminRemark);

    /** 商家提现单分页（创建时间倒序）。 */
    IPage<Withdrawal> pageBySeller(Long sellerId, int page, int size);

    /** 管理端提现单分页（可按状态过滤；创建时间倒序）。 */
    IPage<Withdrawal> pageByStatus(Integer status, int page, int size);
}
