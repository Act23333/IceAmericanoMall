package org.icedamericanomall.domain.repository;

import org.icedamericanomall.domain.entity.SkuEntity;

import java.util.List;
import java.util.Optional;

/**
 * SKU 仓储接口 — Domain 层定义契约
 *
 * 充血 SkuEntity 的持久化通过此仓储完成。
 * Infrastructure 实现使用 MyBatis atomic SQL（乐观锁 version）。
 */
public interface SkuRepository {

    Optional<SkuEntity> findById(Long id);

    List<SkuEntity> findByProductId(Long productId);

    /** 原子扣减库存: UPDATE ... SET stock = stock - #{qty} WHERE id = #{id} AND stock >= #{qty} */
    int deductStockAtomically(Long skuId, int quantity);

    /** 原子恢复库存 */
    int restoreStockAtomically(Long skuId, int quantity);
}
