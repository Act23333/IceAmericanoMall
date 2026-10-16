package org.icedamericanomall.cart.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.cart.app.dto.SkuView;
import org.icedamericanomall.cart.app.port.CartSkuViewQuery;
import org.icedamericanomall.common.utils.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 购物车 SKU 视图查询实现（基础设施层）—— 只读联表读模型（<b>单条 SQL</b>）。
 *
 * <p>列口径（ADR-057 同库只读 + ADR-061 §决策 2/3）：
 * <ul>
 *   <li>展示列：{@code spec/price/image} ← {@code sku}；{@code spu_name} ← {@code spu.name}
 *       （原 {@code product_name}，ADR-061 §决策 5）；</li>
 *   <li>业务键：{@code sku.sku_id} / {@code spu.spu_id}（{@code BINARY(16)} → 32-hex）；
 *       与展示列<b>同一条 SQL</b> 带出，零额外往返；</li>
 *   <li>技术键：{@code sku.id}（与 {@code cart.sku_id} 对齐的内部关联键）。</li>
 * </ul>
 *
 * <p><b>为何 LEFT JOIN 而非 INNER JOIN</b>：{@code cart} 行的 SKU 可能已下架或商品行缺失
 * （脏数据 / 物理删除）—— 购物车<b>必须照常列出</b>该行（前端渲染「已失效」），
 * 商品侧字段为 {@code null}；用 INNER JOIN 会让「商品没了」等价于「购物车少一件」，
 * 违反 ADR-057 fail-open 口径。
 *
 * <p><b>只读</b>：本类仅 SELECT，不写任何表；参数一律走占位符（{@code IN (?,?)}），
 * 不自拼字面量（ADR-050 SQL 字面量禁令）。业务键入参以 {@code byte[]} 与
 * {@code BINARY(16)} 列比较（{@link IdGenerator#toBytesOrNull}），走唯一索引。
 */
@Repository
@RequiredArgsConstructor
public class CartSkuViewQueryImpl implements CartSkuViewQuery {

    /** 按 SKU 条件批量取视图（LEFT JOIN：商品缺失时 SKU 行仍在，商品侧字段为 NULL）。 */
    private static final String SQL_SELECT_VIEW =
            "SELECT s.id AS sku_pk, s.sku_id AS sku_business_id, s.spec AS spec, "
                    + "s.price AS price, s.image AS image, "
                    + "p.spu_id AS spu_business_id, p.name AS spu_name "
                    + "FROM sku s LEFT JOIN spu p ON s.spu_id = p.id WHERE %s IN (%s)";

    /** 技术键过滤列。 */
    private static final String COLUMN_SKU_PK = "s.id";

    /** 业务键过滤列（{@code sku.sku_id BINARY(16)}）。 */
    private static final String COLUMN_SKU_BUSINESS_ID = "s.sku_id";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<Long, SkuView> findBySkuIds(Collection<Long> skuTechnicalIds) {
        List<Long> ids = normalizeTechnicalIds(skuTechnicalIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, SkuView> result = new LinkedHashMap<>();
        for (SkuView row : queryRows(COLUMN_SKU_PK, ids.toArray())) {
            if (row.skuTechnicalId() != null) {
                result.putIfAbsent(row.skuTechnicalId(), row);
            }
        }
        return result;
    }

    @Override
    public Map<String, SkuView> findBySkuBusinessIds(Collection<String> skuBusinessIds) {
        List<byte[]> ids = normalizeBusinessIds(skuBusinessIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, SkuView> result = new LinkedHashMap<>();
        // 结果键用业务键（调用方持有的就是业务键），而非技术键
        for (SkuView row : queryRows(COLUMN_SKU_BUSINESS_ID, ids.toArray())) {
            if (row.skuBusinessId() != null) {
                result.putIfAbsent(row.skuBusinessId(), row);
            }
        }
        return result;
    }

    /** 单条 SQL 执行（占位符个数与参数一一对应）。 */
    private List<SkuView> queryRows(String filterColumn, Object[] params) {
        String placeholders = "?,".repeat(params.length - 1) + "?";
        return jdbcTemplate.query(SQL_SELECT_VIEW.formatted(filterColumn, placeholders), ROW_MAPPER, params);
    }

    private List<Long> normalizeTechnicalIds(Collection<Long> skuTechnicalIds) {
        if (skuTechnicalIds == null || skuTechnicalIds.isEmpty()) {
            return List.of();
        }
        return skuTechnicalIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    /** BINARY(16) → 32-hex → BINARY(16) 的宽松解析（非法入参落「未命中」，不放大成 500）。 */
    private List<byte[]> normalizeBusinessIds(Collection<String> skuBusinessIds) {
        if (skuBusinessIds == null || skuBusinessIds.isEmpty()) {
            return List.of();
        }
        List<byte[]> bytes = new ArrayList<>();
        for (String id : skuBusinessIds) {
            byte[] parsed = IdGenerator.toBytesOrNull(id);
            if (parsed != null && bytes.stream().noneMatch(parsed::equals)) {
                bytes.add(parsed);
            }
        }
        return bytes;
    }

    /** 行映射：技术键 + 两个业务键（BINARY(16) → 32-hex）+ 展示列。 */
    private static final RowMapper<SkuView> ROW_MAPPER = (rs, rowNum) -> new SkuView(
            rs.getObject("sku_pk", Long.class),
            IdGenerator.toHexString(rs.getBytes("sku_business_id")),
            IdGenerator.toHexString(rs.getBytes("spu_business_id")),
            rs.getString("spu_name"),
            rs.getString("spec"),
            rs.getObject("price", Integer.class),
            rs.getString("image"));
}
