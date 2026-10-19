package org.icedamericanomall.trade.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.trade.domain.model.finance.Settlement;
import org.icedamericanomall.trade.domain.model.finance.SettlementRemaining;

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

    /**
     * 取「可结算」结算单的 id 候选（触发入口用；只读）。
     *
     * <p>与 {@link #settleConditionally} 同条件，但<b>它不是判定</b>：候选取出到真正结算之间，
     * 状态可能被别的路径改掉（例如同一批被两个调度实例扫到）—— 那时条件更新返回 0 行、本单跳过，
     * 不会重复记账。故这里不做锁、不做预留。
     *
     * @param today 应用时钟的「今天」
     * @param limit 单批上限（&gt; 0；调用方负责给默认值）
     * @param periodEnd 只扫该账期（{@code null} = 不按账期过滤）；补跑某一天时用它把范围变成显式输入
     * @return 按 id 升序的候选 id；无候选返回空列表（不返回 null）
     */
    List<Long> findSettleableIds(LocalDate today, int limit, LocalDate periodEnd);

    /**
     * 可核销候选：该商家「已结算且还有余额」的结算单，按账期升序（= FIFO 顺序）。
     *
     * <p>只返回 id 与余额两个数：核销规划是纯函数，不需要整个聚合；也不需要在这里判额度
     * （额度由 {@code freeze} 的 SQL 守）。返回空列表表示「没有可核销的单」。
     */
    List<SettlementRemaining> findAllocatable(Long sellerId);
}
