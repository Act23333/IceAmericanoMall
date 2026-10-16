package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.icedamericanomall.item.infrastructure.persistence.SpuPO;

import java.util.List;

public interface SpuMapper extends BaseMapper<SpuPO> {

    /**
     * 按「在售 SKU 最低价」排序的商品分页（sort=price 场景）。
     *
     * <p>{@code orderDirection} 只能取 ASC/DESC，调用方必须先白名单校验，否则存在 SQL 注入风险。
     * 最低价通过 LEFT JOIN 聚合子查询获得，保证排序与分页在数据库侧完成（不能只在当前页内存排序）。
     *
     * @param status          商品状态（{@code SpuStatusEnum}）
     * @param skuStatus       SKU 状态（{@code SkuStatusEnum}），仅统计在售 SKU
     * @param orderDirection  排序方向白名单值：ASC / DESC
     */
    @Select("""
            <script>
            SELECT p.* FROM spu p
            LEFT JOIN (
                SELECT spu_id, MIN(price) AS min_price
                FROM sku WHERE status = #{skuStatus} GROUP BY spu_id
            ) s ON s.spu_id = p.id
            WHERE p.status = #{status}
            <if test="sellerId != null"> AND p.seller_id = #{sellerId} </if>
            <if test="categoryId != null"> AND p.category_id = #{categoryId} </if>
            <if test="keyword != null and keyword != ''"> AND p.name LIKE CONCAT('%', #{keyword}, '%') </if>
            ORDER BY s.min_price ${orderDirection}, p.id DESC
            </script>
            """)
    IPage<SpuPO> selectPageOrderByMinPrice(IPage<SpuPO> page,
                                                   @Param("status") Integer status,
                                                   @Param("skuStatus") Integer skuStatus,
                                                   @Param("sellerId") Long sellerId,
                                                   @Param("categoryId") Long categoryId,
                                                   @Param("keyword") String keyword,
                                                   @Param("orderDirection") String orderDirection);

    /**
     * 商品检索分页（LIKE 降级链路数据源，ADR-052）—— 与 search-service 原直读实现的语义逐条对齐：
     * 在售 + 关键词 LIKE(name/description) + 分类/品牌过滤 + 在售 SKU 最低价区间 +
     * 白名单排序（price=最低价升序 / newest=上架时间降序 / sales|relevance=销量降序）。
     * 排序经静态 {@code <choose>} 分支，无用户输入拼接。
     */
    @Select("""
            <script>
            SELECT p.*, COALESCE((SELECT MIN(s.price) FROM sku s WHERE s.spu_id = p.id AND s.status = 1), 0) AS min_price
            FROM spu p
            WHERE p.status = 1
            <if test="keyword != null and keyword != ''">
              AND (p.name LIKE CONCAT('%', #{keyword}, '%') OR p.description LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="categoryId != null"> AND p.category_id = #{categoryId} </if>
            <if test="brand != null and brand != ''"> AND p.brand = #{brand} </if>
            <if test="minPrice != null"> AND COALESCE((SELECT MIN(s2.price) FROM sku s2 WHERE s2.spu_id = p.id AND s2.status = 1), 0) &gt;= #{minPrice} </if>
            <if test="maxPrice != null"> AND COALESCE((SELECT MIN(s3.price) FROM sku s3 WHERE s3.spu_id = p.id AND s3.status = 1), 0) &lt;= #{maxPrice} </if>
            <choose>
              <when test="sort == 'price'">ORDER BY min_price ASC, p.id DESC</when>
              <when test="sort == 'newest'">ORDER BY p.publish_time DESC, p.id DESC</when>
              <otherwise>ORDER BY p.sold_count DESC, p.id DESC</otherwise>
            </choose>
            </script>
            """)
    IPage<SpuPO> selectSearchPage(IPage<SpuPO> page,
                                      @Param("keyword") String keyword,
                                      @Param("categoryId") Long categoryId,
                                      @Param("brand") String brand,
                                      @Param("minPrice") Integer minPrice,
                                      @Param("maxPrice") Integer maxPrice,
                                      @Param("sort") String sort);

    /** 在售商品全量导出分页（ES 重建数据源，id 升序稳定遍历）。 */
    @Select("""
            <script>
            SELECT p.*, COALESCE((SELECT MIN(s.price) FROM sku s WHERE s.spu_id = p.id AND s.status = 1), 0) AS min_price
            FROM spu p
            WHERE p.status = 1
            ORDER BY p.id ASC
            </script>
            """)
    IPage<SpuPO> selectExportPage(IPage<SpuPO> page);

    /** 单商品检索卡片（publishTime 回填用；含最低价计算列）。 */
    @Select("SELECT p.*, COALESCE((SELECT MIN(s.price) FROM sku s WHERE s.spu_id = p.id AND s.status = 1), 0) AS min_price "
            + "FROM spu p WHERE p.id = #{id}")
    SpuPO selectSearchCardById(@Param("id") Long id);

    /** 商品名前缀联想（在售，销量降序；去重由应用层完成，与原 search 直读口径一致）。 */
    @Select("SELECT name FROM spu WHERE status = 1 AND name LIKE CONCAT(#{prefix}, '%') "
            + "ORDER BY sold_count DESC LIMIT #{limit}")
    List<String> selectSuggestNames(@Param("prefix") String prefix, @Param("limit") int limit);
}
