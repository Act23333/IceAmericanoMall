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

    /** 全量更新（审核回写等）。 */
    void update(Withdrawal withdrawal);

    /** 商家提现单分页（创建时间倒序）。 */
    IPage<Withdrawal> pageBySeller(Long sellerId, int page, int size);

    /** 管理端提现单分页（可按状态过滤；创建时间倒序）。 */
    IPage<Withdrawal> pageByStatus(Integer status, int page, int size);
}
