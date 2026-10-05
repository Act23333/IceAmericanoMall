package org.icedamericanomall.user.domain.repository;

import org.icedamericanomall.user.domain.model.member.MemberGrowthLog;

import java.util.Optional;

/**
 * 会员成长值流水仓储（领域层定义，基础设施层实现；F6 会员等级，ADR-044）。
 *
 * <p>业务唯一键 = {@code orderNo}（{@code uk_order_no} 幂等锚点，一单一次成长）；
 * 用户累计成长值不冗余存列，以 {@code Σ growth_amount} 求和口径由 {@link #sumGrowthByUserId} 提供。
 */
public interface MemberGrowthLogRepository {

    /**
     * 按订单号查流水（幂等锚点定位）。
     *
     * <p><b>锁定读（FOR UPDATE）口径</b>：I46 的并发回落路径需读取「并发胜者已提交」的流水 ——
     * InnoDB REPEATABLE READ 下本事务既有一致性快照，普通快照读对胜者提交的新行不可见，
     * 锁定读读最新已提交版本；顺序重放的前置检查共用同一口径（胜者未提交时阻塞至其提交后
     * 命中重放分支，并发窗口进一步收窄）。
     *
     * @param orderNo 来源订单号
     * @return 命中的流水；不存在返回 empty
     */
    Optional<MemberGrowthLog> findByOrderNo(String orderNo);

    /**
     * 新增流水（技术主键由实现回填）。
     *
     * <p>并发撞 {@code uk_order_no} 时实现向上抛 {@code org.springframework.dao.DuplicateKeyException}，
     * 由应用层捕获后回落为「查现有流水返回」，不向调用方透出
     * （ADR-035 {@code refund_order.uk_after_sale} 幂等先例）。
     *
     * @param growthLog 待持久化流水
     */
    void insert(MemberGrowthLog growthLog);

    /**
     * 用户累计成长值（{@code Σ growth_amount}，分；无流水返回 0）。
     *
     * @param userId 用户技术主键
     * @return 累计成长值（分，≥ 0）
     */
    long sumGrowthByUserId(Long userId);
}
