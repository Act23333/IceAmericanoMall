package org.icedamericanomall.marketing.domain.repository;

import org.icedamericanomall.marketing.domain.model.promotion.Promotion;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 促销活动仓储（领域端口）—— 实现方：{@code infrastructure.persistence.promotion.PromotionRepositoryImpl}。
 *
 * <p>该表无业务唯一键，主键 {@code id} 即其业务标识（V5.8 契约 AD49/AD50 亦以 id 寻址，
 * 与 {@code flash_sale} 同口径）。
 */
public interface PromotionRepository {

    /** 按主键取活动（不存在返回 null）。 */
    Promotion findById(Long id);

    /**
     * 商家当前「进行中」的活动集合（I44 计价查询，ADR-043）。
     *
     * <p>进行中按<b>时间窗推导口径</b>过滤（{@code start_time ≤ now ≤ end_time}），按 id 升序；
     * 不叠加落库 {@code status} 快照条件——快照可能停留在创建时刻的「未开始」态，
     * 时间窗才是状态的权威口径。空集合 = 无生效活动（合法业务态，非查询失败）。
     *
     * @param sellerId 商家 user_id（店铺级活动）
     * @param now      判定时刻
     * @return 进行中活动列表（无则空集合）
     */
    List<Promotion> findActiveBySeller(Long sellerId, LocalDateTime now);

    /**
     * 全店铺当前「进行中」的活动集合（M12 活动中心列表，V5.11）。
     *
     * <p>与 {@link #findActiveBySeller} 同一<b>时间窗推导口径</b>（{@code start_time ≤ now ≤ end_time}），
     * 不叠加落库 {@code status} 快照条件；区别仅在去掉 sellerId 过滤（活动中心为跨店铺聚合展示）。
     * 排序固定为 {@code start_time} 降序（最新活动优先，活动中心展示惯例；次序 {@code id} 降序保证稳定）。
     * 空集合 = 无生效活动（合法业务态，非查询失败）。
     *
     * @param now 判定时刻
     * @return 进行中活动列表（无则空集合）
     */
    List<Promotion> findAllActive(LocalDateTime now);

    /**
     * 新增活动（INSERT；生成的主键回填到聚合，供创建用例组装 VO）。
     *
     * @param promotion 待保存的活动聚合
     */
    void save(Promotion promotion);

    /**
     * 更新活动（乐观锁：版本号随聚合携带，冲突由 MyBatis-Plus 拦截器拦截）。
     *
     * @param promotion 合并后的活动聚合（全量字段）
     */
    void update(Promotion promotion);

    /**
     * 物理删除活动（表无逻辑删除列；「进行中禁删」守卫在应用层）。
     *
     * @param id 活动主键
     */
    void deleteById(Long id);
}
