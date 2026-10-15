package org.icedamericanomall.user.infrastructure.persistence.favorite;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.app.dto.ProductBriefView;
import org.icedamericanomall.user.app.port.FavoriteProductBriefQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商品展示摘要查询实现（基础设施层）—— 只读联表读模型（单条 SQL）。
 *
 * <p>口径与同服务的足迹读模型 {@code HistoryViewQueryImpl} 一致：
 * <ul>
 *   <li>{@code product_name} ← {@code product.name}；{@code main_image} ← {@code product.main_image}；</li>
 *   <li>{@code price} ← 在售 SKU 最低价（{@code MIN(sku.price) WHERE sku.status = 1}），无在售 SKU 为 null；</li>
 *   <li>{@code status} ← {@code product.status}（1=上架 / 2=下架 / 3=删除），<b>不过滤</b>——
 *       下架与已删除商品仍需返回行（前端据 status 渲染「已失效」），收藏列表不因商品失效而少行。</li>
 * </ul>
 *
 * <p><b>只读</b>：本类仅 SELECT，不写任何 item 域表（ADR-052 豁免边界的只读要求）。
 * 参数一律走占位符（{@code IN (?,?)}）——不自拼字面量（ADR-050 SQL 字面量禁令）。
 */
@Repository
@RequiredArgsConstructor
public class FavoriteProductBriefQueryImpl implements FavoriteProductBriefQuery {

    /** 在售 SKU 最低价子查询（与 HistoryViewQueryImpl 同一口径）。 */
    private static final String SQL_SELECT_BRIEF =
            "SELECT p.id AS product_id, p.name AS product_name, p.main_image, p.status, "
                    + "(SELECT MIN(s.price) FROM sku s WHERE s.product_id = p.id AND s.status = 1) AS price "
                    + "FROM product p WHERE p.id IN (%s)";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<Long, ProductBriefView> findByIds(Collection<Long> productIds) {
        List<Long> ids = normalize(productIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<ProductBriefView> rows = jdbcTemplate.query(
                SQL_SELECT_BRIEF.formatted(placeholders),
                (rs, rowNum) -> new ProductBriefView(
                        rs.getObject("product_id", Long.class),
                        rs.getString("product_name"),
                        rs.getString("main_image"),
                        rs.getObject("price", Integer.class),
                        rs.getObject("status", Integer.class)),
                ids.toArray());
        return rows.stream()
                .filter(row -> row.productId() != null)
                .collect(Collectors.toMap(ProductBriefView::productId, row -> row, (first, ignored) -> first));
    }

    private List<Long> normalize(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return productIds.stream().filter(Objects::nonNull).distinct().toList();
    }
}
