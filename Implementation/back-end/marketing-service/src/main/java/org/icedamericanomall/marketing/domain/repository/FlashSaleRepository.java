package org.icedamericanomall.marketing.domain.repository;

import org.icedamericanomall.marketing.domain.model.flash.FlashSale;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀场次仓储（领域端口）—— 实现方：{@code infrastructure.persistence.flash.FlashSaleRepositoryImpl}。
 */
public interface FlashSaleRepository {

    /** 按主键取场次（不存在返回 null）。 */
    FlashSale findById(Long id);

    /** 进行中的场次（时间窗 + 状态，均为 DB 口径）。 */
    List<FlashSale> findActive(LocalDateTime now);

    /**
     * 异步统计 soldCount +1（乐观锁 + 库存守卫，条件全部满足才更新）。
     *
     * @return true=更新成功；false=版本冲突或已售罄（调用方按重试/跳过处理）
     */
    boolean incrementSoldCountIfBelowStock(Long id, Integer version, int stock);

    /**
     * 新增场次（INSERT；生成的主键回填到聚合，供创建用例组装 VO）。
     *
     * @param flashSale 待保存的场次聚合
     */
    void save(FlashSale flashSale);

    /**
     * 更新场次（乐观锁：版本号随聚合携带，冲突由 MyBatis-Plus 拦截器拦截）。
     *
     * <p>该表无业务唯一键，主键 {@code id} 即其业务标识（V5.7 契约 AD41/AD42 亦以 id 寻址）。
     *
     * @param flashSale 合并后的场次聚合（全量字段）
     */
    void update(FlashSale flashSale);

    /**
     * 物理删除场次（表无逻辑删除列；「进行中禁删」守卫在应用层）。
     *
     * @param id 场次主键
     */
    void deleteById(Long id);
}
