package org.icedamericanomall.marketing.infrastructure.persistence.flash;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief;
import org.icedamericanomall.marketing.app.port.FlashSaleSpuBriefQuery;
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
 * 秒杀商品摘要查询实现（基础设施层）—— 只读联表读模型（<b>单条 SQL</b>）。
 *
 * <p>列口径（契约 §4.10.2 + ADR-057 + ADR-061）：
 * <ul>
 *   <li>展示列：{@code spu_name} ← {@code spu.name}、{@code main_image} ← {@code spu.main_image}
 *       （<b>线上名统一 {@code main_image}</b>：与 DB 列及其余 VO 同名，消除方言 ——
 *       原 {@code product_image}/{@code spu_image} 两种叫法已废弃）、
 *       {@code original_price} ← {@code sku.original_price}
 *       （未设划线价时为 {@code null}，<b>不回落</b>为售价 —— 划线价缺省时前端不渲染划线）；</li>
 *   <li>业务键：{@code sku.sku_id} / {@code spu.spu_id}（{@code BINARY(16)} → 32-hex），
 *       与展示列<b>同一条 SQL</b> 带出（ADR-058 §3：读模型必须顺带返回业务标识）。</li>
 * </ul>
 *
 * <p><b>为何内连接</b>：商品已物理删除的 SKU 不出现，调用方按缺失置空（展示回填 fail-open）；
 * 这与「秒杀场次本体照常列出」不冲突 —— 场次行来自 {@code flash_sale} 表，本端口只补展示列。
 *
 * <p><b>只读</b>：本类仅 SELECT，不写任何 item 域表。参数一律走占位符
 * （{@code IN (?,?)}）——不自拼字面量（ADR-050 SQL 字面量禁令）；业务键入参以
 * {@code byte[]} 与 {@code BINARY(16)} 列比较。
 */
@Repository
@RequiredArgsConstructor
public class FlashSaleSpuBriefQueryImpl implements FlashSaleSpuBriefQuery {

    /** 展示摘要查询模板（{@code %s} = 过滤列，{@code %s} = 占位符串）。 */
    private static final String SQL_SELECT_BRIEF =
            "SELECT s.id AS sku_pk, s.sku_id AS sku_business_id, p.id AS spu_pk, "
                    + "p.spu_id AS spu_business_id, p.name AS spu_name, p.main_image AS main_image, "
                    + "s.original_price AS original_price "
                    + "FROM sku s JOIN spu p ON s.spu_id = p.id WHERE %s IN (%s)";

    /** 技术键过滤列。 */
    private static final String COLUMN_SKU_PK = "s.id";

    /** 业务键过滤列（{@code sku.sku_id BINARY(16)}）。 */
    private static final String COLUMN_SKU_BUSINESS_ID = "s.sku_id";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<Long, FlashSaleSpuBrief> findBySkuIds(Collection<Long> skuIds) {
        List<Long> ids = normalize(skuIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, FlashSaleSpuBrief> result = new LinkedHashMap<>();
        for (FlashSaleSpuBrief row : queryRows(COLUMN_SKU_PK, ids.toArray())) {
            if (row.skuId() != null) {
                result.putIfAbsent(row.skuId(), row);
            }
        }
        return result;
    }

    @Override
    public Map<String, FlashSaleSpuBrief> findBySkuBusinessIds(Collection<String> skuBusinessIds) {
        List<byte[]> ids = normalizeBusinessIds(skuBusinessIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, FlashSaleSpuBrief> result = new LinkedHashMap<>();
        // 结果键用业务键（调用方持有的就是业务键）
        for (FlashSaleSpuBrief row : queryRows(COLUMN_SKU_BUSINESS_ID, ids.toArray())) {
            if (row.skuBusinessId() != null) {
                result.putIfAbsent(row.skuBusinessId(), row);
            }
        }
        return result;
    }

    /** 单条 SQL 执行（占位符个数与参数一一对应）。 */
    private List<FlashSaleSpuBrief> queryRows(String filterColumn, Object[] params) {
        String placeholders = "?,".repeat(params.length - 1) + "?";
        return jdbcTemplate.query(SQL_SELECT_BRIEF.formatted(filterColumn, placeholders), ROW_MAPPER, params);
    }

    private List<Long> normalize(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return skuIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
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

    /** 行映射：技术键（内部关联/解析）+ 两个业务键（BINARY(16) → 32-hex）+ 展示列。 */
    private static final RowMapper<FlashSaleSpuBrief> ROW_MAPPER = (rs, rowNum) -> new FlashSaleSpuBrief(
            rs.getObject("sku_pk", Long.class),
            rs.getObject("spu_pk", Long.class),
            IdGenerator.toHexString(rs.getBytes("sku_business_id")),
            IdGenerator.toHexString(rs.getBytes("spu_business_id")),
            rs.getString("spu_name"),
            rs.getString("main_image"),
            rs.getObject("original_price", Integer.class));
}
