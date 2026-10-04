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
}
