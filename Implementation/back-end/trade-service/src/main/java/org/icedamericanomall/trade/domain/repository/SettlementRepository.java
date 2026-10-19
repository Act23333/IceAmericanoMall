package org.icedamericanomall.trade.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.trade.domain.model.finance.Settlement;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /** 商家指定状态的结算单列表（口径查询：对账/自检按状态取数）。 */
    List<Settlement> findBySellerAndStatus(Long sellerId, Integer status);

    /**
     * 条件迁移 PENDING→SETTLED —— <b>该迁移的唯一原子入口</b>
     * （定时任务与手动兜底都调它，触发方式后定；不允许别处直接改 status）。
     *
     * <p>可结算条件全部字段化，一条语句 + 影响行数判定（与 {@code deductStock} 同形态）：
     * <ol>
     *   <li>{@code status = PENDING} —— 幂等：已结算/已打款的再调必为 0 行；</li>
     *   <li>{@code period_end <= today} —— 账期已走完（时间条件，不是「看起来该结了」）；</li>
     *   <li>{@code settlement_amount > 0} —— 零额单不结算；</li>
     *   <li>{@code order_count > 0} —— 无订单的账期不结算。</li>
     * </ol>
     *
     * @param id         结算单技术主键
     * @param fromStatus 期望的当前状态（PENDING）
     * @param toStatus   目标状态（SETTLED）
     * @param today      应用时钟的「今天」（账期判定用）
     * @param now        更新时刻
     * @return 影响行数：1=本次真的完成结算；0=条件不满足（幂等 no-op，不抛异常）
     */
    int settleConditionally(Long id, Integer fromStatus, Integer toStatus, LocalDate today, LocalDateTime now);
}
