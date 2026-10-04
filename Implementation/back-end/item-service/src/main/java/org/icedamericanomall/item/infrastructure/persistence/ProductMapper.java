package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.icedamericanomall.item.infrastructure.persistence.ProductPO;

public interface ProductMapper extends BaseMapper<ProductPO> {

    /**
     * 按「在售 SKU 最低价」排序的商品分页（sort=price 场景）。
     *
     * <p>{@code orderDirection} 只能取 ASC/DESC，调用方必须先白名单校验，否则存在 SQL 注入风险。
     * 最低价通过 LEFT JOIN 聚合子查询获得，保证排序与分页在数据库侧完成（不能只在当前页内存排序）。
     *
     * @param status          商品状态（{@code ProductStatusEnum}）
     * @param skuStatus       SKU 状态（{@code SkuStatusEnum}），仅统计在售 SKU
     * @param orderDirection  排序方向白名单值：ASC / DESC
     */
    @Select("""
            <script>
            SELECT p.* FROM product p
            LEFT JOIN (
                SELECT product_id, MIN(price) AS min_price
                FROM sku WHERE status = #{skuStatus} GROUP BY product_id
            ) s ON s.product_id = p.id
            WHERE p.status = #{status}
            <if test="sellerId != null"> AND p.seller_id = #{sellerId} </if>
            <if test="categoryId != null"> AND p.category_id = #{categoryId} </if>
            <if test="keyword != null and keyword != ''"> AND p.name LIKE CONCAT('%', #{keyword}, '%') </if>
            ORDER BY s.min_price ${orderDirection}, p.id DESC
            </script>
            """)
    IPage<ProductPO> selectPageOrderByMinPrice(IPage<ProductPO> page,
                                                   @Param("status") Integer status,
                                                   @Param("skuStatus") Integer skuStatus,
                                                   @Param("sellerId") Long sellerId,
                                                   @Param("categoryId") Long categoryId,
                                                   @Param("keyword") String keyword,
                                                   @Param("orderDirection") String orderDirection);
}
