package org.icedamericanomall.trade.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.trade.domain.model.aftersale.AfterSale;

import java.util.List;

/**
 * 售后单仓储（领域端口）—— 实现方：{@code infrastructure.persistence.aftersale.AfterSaleRepositoryImpl}。
 *
 * <p>状态迁移走条件更新（{@link #updateStatusConditionally} / {@link #updateStatusConditionallyFrom}），
 * 「WHERE 带期望状态」是售后状态机并发安全的唯一出口；0 行 = 未命中。
 */
public interface AfterSaleRepository {

    /** 按主键取售后单（不存在返回 null）。 */
    AfterSale findById(Long id);

    /** 保存售后单（id 回填）。 */
    AfterSale save(AfterSale afterSale);

    /** 全量更新（审核回写等）。 */
    void update(AfterSale afterSale);

    /** 同订单同用户是否已有售后申请（重复申请去重）。 */
    boolean existsByOrderNoAndUserId(String orderNo, Long userId);

    /** 用户售后单分页（创建时间倒序）。 */
    IPage<AfterSale> pageByUserId(Long userId, int page, int size);

    /** 管理端售后单分页（可按状态过滤；创建时间倒序）。 */
    IPage<AfterSale> pageByStatus(Integer status, int page, int size);

    /**
     * 售后单状态「条件迁移」—— 仅当当前状态等于期望值时推进。
     *
     * @return 受影响行数（1 = 命中并迁移；0 = 状态已变化 / 不匹配）
     */
    int updateStatusConditionally(Long id, int expectedStatus, int targetStatus);

    /**
     * 售后单状态「多来源条件迁移」—— 当前状态命中 {@code IN (expectedStatuses)} 时才推进。
     *
     * @return 受影响行数（1 = 命中并迁移；0 = 状态已迁移 / 不属于任一来源）
     */
    int updateStatusConditionallyFrom(Long id, List<Integer> expectedStatuses, int targetStatus);
}
