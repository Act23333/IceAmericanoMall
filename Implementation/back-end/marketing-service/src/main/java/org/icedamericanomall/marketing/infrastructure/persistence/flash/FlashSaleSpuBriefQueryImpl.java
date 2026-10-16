package org.icedamericanomall.marketing.infrastructure.persistence.flash;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief;
import org.icedamericanomall.marketing.app.port.FlashSaleSpuBriefQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 秒杀商品展示摘要查询实现（基础设施层）—— 只读联表读模型（单条 SQL）。
 *
 * <p>字段口径（契约 §4.10.2 {@code FlashSaleVO}）：
 * <ul>
 *   <li>{@code product_name} ← {@code spu.name}；{@code product_image} ← {@code spu.main_image}；</li>
 *   <li>{@code original_price} ← {@code sku.original_price}（划线原价，分）；未设划线价即 null，
 *       <b>不回落</b>为售价（划线价缺省时前端不渲染划线）。</li>
 * </ul>
 *
 * <p><b>只读</b>：本类仅 SELECT，不写任何 item 域表。参数一律走占位符
 * （{@code IN (?,?)}）——不自拼字面量（ADR-050 SQL 字面量禁令）。
 */
@Repository
@RequiredArgsConstructor
public class FlashSaleSpuBriefQueryImpl implements FlashSaleSpuBriefQuery {

    /** 按 SKU ID 批量取商品摘要（内连接：商品已物理删除的 SKU 不出现，调用方按缺失置空）。 */
    private static final String SQL_SELECT_BRIEF =
            "SELECT s.id AS sku_id, p.name AS product_name, p.main_image AS product_image, "
                    + "s.original_price AS original_price "
                    + "FROM sku s JOIN spu p ON s.spu_id = p.id WHERE s.id IN (%s)";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<Long, FlashSaleSpuBrief> findBySkuIds(Collection<Long> skuIds) {
        List<Long> ids = normalize(skuIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<FlashSaleSpuBrief> rows = jdbcTemplate.query(
                SQL_SELECT_BRIEF.formatted(placeholders),
                (rs, rowNum) -> new FlashSaleSpuBrief(
                        rs.getObject("sku_id", Long.class),
                        rs.getString("product_name"),
                        rs.getString("product_image"),
                        rs.getObject("original_price", Integer.class)),
                ids.toArray());
        return rows.stream()
                .filter(row -> row.skuId() != null)
                .collect(Collectors.toMap(FlashSaleSpuBrief::skuId, row -> row, (first, ignored) -> first));
    }

    private List<Long> normalize(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return skuIds.stream().filter(Objects::nonNull).distinct().toList();
    }
}
