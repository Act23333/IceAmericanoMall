package org.icedamericanomall.item.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.item.app.dto.SkuSpuDisplay;
import org.icedamericanomall.item.app.port.SkuSpuDisplayQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SKU 所属商品展示事实查询实现（基础设施层）—— 只读联表读模型（<b>单条 SQL</b>）。
 *
 * <p>字段口径（契约 §4.14.5 I13 {@code SkuCO}）：
 * <ul>
 *   <li>{@code spu_name} ← {@code spu.name}；{@code seller_id} ← {@code spu.seller_id}；
 *       {@code category_id} ← {@code spu.category_id}；</li>
 *   <li>{@code freight_template_id} ← {@code spu.freight_template_id}
 *       （未绑定为 {@code null}，<b>不回落</b>；回落商家默认模板属计价侧口径，ADR-037）；</li>
 *   <li><b>业务键</b>：{@code sku_id} ← {@code sku.sku_id}、{@code spu_id} ← {@code spu.spu_id}
 *       （均 {@code BINARY(16)} → 32-hex，ADR-050/058 §3/061）。同一条 SQL 顺带带出，零额外往返。</li>
 * </ul>
 *
 * <p><b>为何 LEFT JOIN 而非 INNER JOIN</b>：I13 的调用方（购物车回填、下单取数）必须先拿到
 * SKU 本体（价格/库存/规格）再谈展示字段 —— 商品行缺失（脏数据/物理删除）时 SKU <b>必须照常返回</b>，
 * 商品侧字段为 {@code null}；用 INNER JOIN 会让「商品没了」等价于「SKU 不存在」，把
 * 展示字段的缺失放大成下单失败（违反 ADR-057 fail-open 口径）。
 *
 * <p><b>两种入参口径</b>（ADR-061：入参收业务键、同库内部保持技术键）：
 * <ul>
 *   <li>{@link #findBySkuIds} —— 技术键（{@code sku.id}）批量，供已在技术键空间的调用方；</li>
 *   <li>{@link #findBySkuBusinessIds} —— 业务键（{@code sku.sku_id} BINARY(16)）批量，
 *       供线格式解析；参数传 {@code byte[]}（{@link IdGenerator#toBytes}）走唯一索引，
 *       与按技术键性能等价。</li>
 * </ul>
 *
 * <p><b>只读</b>：本类仅 SELECT，不写任何表。参数一律走占位符（{@code IN (?,?)}），
 * 不自拼字面量（ADR-050 SQL 字面量禁令）。
 */
@Repository
@RequiredArgsConstructor
public class SkuSpuDisplayQueryImpl implements SkuSpuDisplayQuery {

    /** 按 SKU 条件批量取商品列（LEFT JOIN：商品缺失时 SKU 行仍在，商品侧字段为 NULL）。 */
    private static final String SQL_SELECT_DISPLAY =
            "SELECT s.id AS sku_pk, s.sku_id AS sku_business_id, s.spu_id AS spu_pk, "
                    + "p.spu_id AS spu_business_id, p.name AS spu_name, p.seller_id AS seller_id, "
                    + "p.category_id AS category_id, p.freight_template_id AS freight_template_id "
                    + "FROM sku s LEFT JOIN spu p ON s.spu_id = p.id WHERE %s IN (%s)";

    /** 技术键过滤列。 */
    private static final String COLUMN_SKU_PK = "s.id";

    /** 业务键过滤列（BINARY(16)）。 */
    private static final String COLUMN_SKU_BUSINESS_ID = "s.sku_id";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<Long, SkuSpuDisplay> findBySkuIds(Collection<Long> skuIds) {
        List<Long> ids = normalize(skuIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SkuSpuDisplay> rows = queryRows(COLUMN_SKU_PK, ids.size(), ids.toArray());
        Map<Long, SkuSpuDisplay> result = new LinkedHashMap<>();
        for (SkuSpuDisplay row : rows) {
            if (row.skuId() != null) {
                result.putIfAbsent(row.skuId(), row);
            }
        }
        return result;
    }

    @Override
    public Map<String, SkuSpuDisplay> findBySkuBusinessIds(Collection<String> skuBusinessIds) {
        List<byte[]> ids = normalizeBusinessIds(skuBusinessIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SkuSpuDisplay> rows = queryRows(COLUMN_SKU_BUSINESS_ID, ids.size(), ids.toArray());
        Map<String, SkuSpuDisplay> result = new LinkedHashMap<>();
        // 注意：用业务键（而非技术键）作结果键 —— 调用方持有的是业务键
        for (SkuSpuDisplay row : rows) {
            if (row.skuBusinessId() != null) {
                result.putIfAbsent(row.skuBusinessId(), row);
            }
        }
        return result;
    }

    /** 单条 SQL 执行（占位符个数与参数一一对应）。 */
    private List<SkuSpuDisplay> queryRows(String filterColumn, int count, Object[] params) {
        String placeholders = "?,".repeat(count - 1) + "?";
        return jdbcTemplate.query(SQL_SELECT_DISPLAY.formatted(filterColumn, placeholders), ROW_MAPPER, params);
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

    private List<Long> normalize(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return skuIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    /** 行映射：技术键 + 业务键（BINARY(16) → 32-hex）+ 商品侧展示列。 */
    private static final RowMapper<SkuSpuDisplay> ROW_MAPPER = (rs, rowNum) -> new SkuSpuDisplay(
            rs.getObject("sku_pk", Long.class),
            IdGenerator.toHexString(rs.getBytes("sku_business_id")),
            rs.getObject("spu_pk", Long.class),
            IdGenerator.toHexString(rs.getBytes("spu_business_id")),
            rs.getString("spu_name"),
            rs.getObject("seller_id", Long.class),
            rs.getObject("category_id", Long.class),
            rs.getObject("freight_template_id", Long.class));
}
