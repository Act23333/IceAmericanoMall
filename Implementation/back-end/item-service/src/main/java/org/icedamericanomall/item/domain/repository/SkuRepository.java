package org.icedamericanomall.item.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.item.domain.model.sku.Sku;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Optional;

/**
 * SKU 仓储（领域端口）—— 实现方：{@code infrastructure.persistence.SkuRepositoryImpl}。
 *
 * <p><b>并发语义（ADR-020）</b>：库存扣减/恢复是单条原子 SQL（{@code WHERE stock >= qty}），
 * 返回受影响行数：0 = 库存不足 / 已停售 / 已删除。「不限量 SKU 仅累加销量」由应用层
 * 依据聚合的 {@code isUnlimited()} 判定后选择对应原子方法。
 */
public interface SkuRepository {

    /** 按 ID 载入（扣减/恢复前取聚合判定 isUnlimited）。 */
    Optional<Sku> findById(Long skuId);

    /** 批量按 ID 载入。 */
    List<Sku> findByIds(List<Long> skuIds);

    /** 商品全部 SKU（详情页，不分状态）。 */
    List<Sku> findByProductId(Long productId);

    /** 商品在售 SKU（列表/详情最低价）。 */
    List<Sku> findOnSaleByProductId(Long productId);

    /** 批量在售 SKU（最低价聚合源）。 */
    List<Sku> findOnSaleByProductIds(List<Long> productIds);

    /** 新增（返回回填技术主键后的聚合）。 */
    Sku save(Sku sku);

    /** 原子扣库存（WHERE stock>=qty AND status=1）；0 = 库存不足/停售/不存在。 */
    int deductStock(Long skuId, int quantity);

    /** 原子恢复库存；0 = SKU 已不存在（幂等跳过）。 */
    int restoreStock(Long skuId, int quantity);

    /** 不限量 SKU 销量累加。 */
    int increaseSoldCount(Long skuId, int quantity);

    /** 不限量 SKU 销量回退。 */
    int decreaseSoldCount(Long skuId, int quantity);
}
