package org.icedamericanomall.marketing.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupBuyActivity;

import java.time.LocalDateTime;

/**
 * 拼团活动仓储（领域端口）—— 实现方：{@code infrastructure.persistence.groupbuy.GroupBuyActivityRepositoryImpl}。
 *
 * <p>该表无业务唯一键，主键 {@code id} 即其业务标识（契约 AD55/AD56 以 id 寻址，
 * 与 {@code promotion}/{@code flash_sale} 同口径）。
 */
public interface GroupBuyActivityRepository {

    /**
     * 进行中活动分页（M15 C 端浏览）。
     *
     * <p>「进行中」按<b>时间窗推导口径</b>过滤（{@code start_time ≤ now ≤ end_time}），
     * 不叠加落库 {@code status} 快照条件——快照可能停留在创建时刻的「未开始」态，
     * 时间窗才是状态的权威口径；{@code id} 降序稳定排序。
     *
     * @param now      判定时刻
     * @param pageNum  页码（1 起）
     * @param pageSize 页大小
     * @return 进行中活动分页（无则空页，合法业务态）
     */
    IPage<GroupBuyActivity> findActiveActivities(LocalDateTime now, int pageNum, int pageSize);

    /** 按主键取活动（不存在返回 null）。 */
    GroupBuyActivity findById(Long id);

    /**
     * 新增活动（INSERT；生成的主键回填到聚合，供创建用例组装 VO）。
     *
     * @param activity 待保存的活动聚合
     */
    void save(GroupBuyActivity activity);

    /**
     * 更新活动（乐观锁：版本号随聚合携带，冲突由 MyBatis-Plus 拦截器拦截）。
     *
     * @param activity 合并后的活动聚合（全量字段）
     */
    void update(GroupBuyActivity activity);

    /**
     * 物理删除活动（表无逻辑删除列；「进行中禁删」守卫在应用层）。
     *
     * @param id 活动主键
     */
    void deleteById(Long id);
}
