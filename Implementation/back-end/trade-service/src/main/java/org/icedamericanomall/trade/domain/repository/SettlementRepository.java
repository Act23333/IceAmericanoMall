package org.icedamericanomall.trade.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.trade.domain.model.finance.Settlement;

import java.util.List;

/**
 * 商家结算单仓储（领域端口）—— 实现方：{@code infrastructure.persistence.finance.SettlementRepositoryImpl}。
 */
public interface SettlementRepository {

    /** 保存结算单（id 回填）。 */
    Settlement save(Settlement settlement);

    /** 按主键取结算单（不存在返回 null）。 */
    Settlement findById(Long id);

    /** 商家结算单分页（创建时间倒序）。 */
    IPage<Settlement> pageBySeller(Long sellerId, int page, int size);

    /** 管理后台全部结算单分页（创建时间倒序）。 */
    IPage<Settlement> pageAll(int page, int size);

    /** 商家指定状态的结算单列表（可用余额/级联打款的口径）。 */
    List<Settlement> findBySellerAndStatus(Long sellerId, Integer status);

    /** 将商家指定状态（已结算）的结算单批量置为目标状态（打款成功级联）。 */
    void updateStatusBySellerAndStatus(Long sellerId, Integer fromStatus, Integer toStatus);
}
