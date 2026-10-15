package org.icedamericanomall.item.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.app.dto.SkuProductDisplay;
import org.icedamericanomall.item.app.port.SkuProductDisplayQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SKU 所属商品展示事实查询实现（基础设施层）—— 只读联表读模型（<b>单条 SQL</b>）。
 *
 * <p>字段口径（契约 §4.14.5 I13 {@code SkuCO}）：
 * <ul>
 *   <li>{@code product_name} ← {@code product.name}；{@code seller_id} ← {@code product.seller_id}；
 *       {@code category_id} ← {@code product.category_id}；</li>
 *   <li>{@code freight_template_id} ← {@code product.freight_template_id}
 *       （未绑定为 {@code null}，<b>不回落</b>；回落商家默认模板属计价侧口径，ADR-037）。</li>
 * </ul>
 *
 * <p><b>为何 LEFT JOIN 而非 INNER JOIN</b>：I13 的调用方（购物车回填、下单取数）必须先拿到
 * SKU 本体（价格/库存/规格）再谈展示字段 —— 商品行缺失（脏数据/物理删除）时 SKU <b>必须照常返回</b>，
 * 四字段为 {@code null}；用 INNER JOIN 会让「商品没了」等价于「SKU 不存在」，把
 * 展示字段的缺失放大成下单失败（违反 ADR-057 fail-open 口径）。
 *
 * <p><b>只读</b>：本类仅 SELECT，不写任何表。参数一律走占位符（{@code IN (?,?)}），
 * 不自拼字面量（ADR-050 SQL 字面量禁令）。
 */
@Repository
@RequiredArgsConstructor
public class SkuProductDisplayQueryImpl implements SkuProductDisplayQuery {

    /** 按 SKU ID 批量取商品列（LEFT JOIN：商品缺失时 SKU 行仍在，四字段为 NULL）。 */
    private static final String SQL_SELECT_DISPLAY =
            "SELECT s.id AS sku_id, p.name AS product_name, p.seller_id AS seller_id, "
                    + "p.category_id AS category_id, p.freight_template_id AS freight_template_id "
                    + "FROM sku s LEFT JOIN product p ON s.product_id = p.id WHERE s.id IN (%s)";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<Long, SkuProductDisplay> findBySkuIds(Collection<Long> skuIds) {
        List<Long> ids = normalize(skuIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<SkuProductDisplay> rows = jdbcTemplate.query(
                SQL_SELECT_DISPLAY.formatted(placeholders),
                (rs, rowNum) -> new SkuProductDisplay(
                        rs.getObject("sku_id", Long.class),
                        rs.getString("product_name"),
                        rs.getObject("seller_id", Long.class),
                        rs.getObject("category_id", Long.class),
                        rs.getObject("freight_template_id", Long.class)),
                ids.toArray());
        return rows.stream()
                .filter(row -> row.skuId() != null)
                .collect(Collectors.toMap(SkuProductDisplay::skuId, row -> row, (first, ignored) -> first));
    }

    private List<Long> normalize(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return skuIds.stream().filter(Objects::nonNull).distinct().toList();
    }
}
