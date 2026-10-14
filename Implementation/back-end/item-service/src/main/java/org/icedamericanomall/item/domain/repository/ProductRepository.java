package org.icedamericanomall.item.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.item.domain.model.PageResult;
import org.icedamericanomall.item.domain.model.product.Product;
import org.icedamericanomall.item.domain.model.product.ProductSearchCard;
import org.icedamericanomall.item.domain.model.product.ProductSearchCriteria;

import java.util.List;
import java.util.Map;

/**
 * 商品仓储（领域端口）—— 实现方：{@code infrastructure.persistence.ProductRepositoryImpl}。
 *
 * <p>分页返回 MyBatis-Plus {@code IPage} 信封：商品列表是既有 HTTP 契约（有界务实豁免）。
 */
public interface ProductRepository {

    /** 商品分页（在售；sort=sales 按销量 / 缺省广告优先+创建时间倒序）。 */
    IPage<Product> pageOnSale(Long sellerId, Long categoryId, String keyword, String sort, int page, int size);

    /**
     * 全状态商品分页（不限在售，id 升序稳定遍历）—— AD64 图边全量重建的真相源扫描入口
     * （在售 upsert 直建边 / 下架删除清边，投影镜像增量摄取口径）。
     */
    IPage<Product> pageAll(int pageNum, int pageSize);

    /** 商品分页（按在售 SKU 最低价排序，跨页顺序正确；自定义 SQL 在 Mapper）。 */
    IPage<Product> pageOrderByMinPrice(int page, int size, Long sellerId, Long categoryId,
                                       String keyword, String orderDirection);

    /** 按 ID 载入（可为 null；DELETED 态判定由应用层做）。 */
    Product findById(Long id);

    /** 批量按 ID 载入。 */
    List<Product> findAllByIds(List<Long> ids);

    /** 新增（返回回填技术主键后的聚合）。 */
    Product save(Product product);

    /** 更新状态。 */
    void updateStatus(Long productId, Integer status);

    /**
     * 可编辑字段条件覆盖更新（null = 保持原值；归属校验由应用层先行完成）。
     */
    void updateEditable(Long productId, String name, String mainImage, String description,
                        String brand, Long categoryId, Long freightTemplateId);

    /** 评论数 +1（评价创建联动）。 */
    void incrementCommentCount(Long productId);

    /** 商品是否存在。 */
    boolean existsById(Long productId);

    /** 批量 sellerId 映射（内部契约 I40 回填）。 */
    Map<Long, Long> sellerIdMapByProductIds(List<Long> productIds);

    /** 批量 categoryId 映射（内部契约回填）。 */
    Map<Long, Long> categoryIdMapByProductIds(List<Long> productIds);

    /** 批量运费模板映射（未绑定的商品不进入映射 = 回落商家默认模板）。 */
    Map<Long, Long> freightTemplateIdMapByProductIds(List<Long> productIds);

    // ==================== 检索出口（ADR-052：search-service 数据面） ====================

    /**
     * 商品检索分页（LIKE 降级链路数据源）—— 新方法采用框架无关 {@link PageResult}
     * （域层 IPage 为架构守卫冻结基线债务，只减不增；迁移方向见 ADR-052/11-Test-Strategy）。
     */
    PageResult<ProductSearchCard> searchPage(ProductSearchCriteria criteria);

    /** 在售商品全量导出分页（ES 重建数据源，id 升序稳定遍历）。 */
    PageResult<ProductSearchCard> exportPage(int page, int size);

    /** 商品名前缀联想（在售，销量降序，去重截断）。 */
    List<String> suggestNames(String prefix, int limit);

    /** 单商品检索卡片（ES 摄取 publishTime 回填用）；不存在返回 null。 */
    ProductSearchCard findSearchCardById(Long id);
}
