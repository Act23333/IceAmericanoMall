package org.icedamericanomall.user.infrastructure.persistence.favorite;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.user.app.dto.SpuBriefView;
import org.icedamericanomall.user.app.port.FavoriteSpuBriefQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
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
 *   <li>{@code product_name} ← {@code spu.name}；{@code main_image} ← {@code spu.main_image}；</li>
 *   <li>{@code price} ← 在售 SKU 最低价（{@code MIN(sku.price) WHERE sku.status = 1}），无在售 SKU 为 null；</li>
 *   <li>{@code status} ← {@code spu.status}（1=上架 / 2=下架 / 3=删除），<b>不过滤</b>——
 *       下架与已删除商品仍需返回行（前端据 status 渲染「已失效」），收藏列表不因商品失效而少行。</li>
 * </ul>
 *
 * <p><b>只读</b>：本类仅 SELECT，不写任何 item 域表（ADR-052 豁免边界的只读要求）。
 * 参数一律走占位符（{@code IN (?,?)}）——不自拼字面量（ADR-050 SQL 字面量禁令）。
 */
@Repository
@RequiredArgsConstructor
public class FavoriteSpuBriefQueryImpl implements FavoriteSpuBriefQuery {

    /** 展示摘要查询模板（{@code %s} = 过滤列，{@code %s} = 占位符串）。 */
    private static final String SQL_SELECT_BRIEF =
            "SELECT p.id AS spu_pk, p.spu_id AS spu_business_id, p.name AS product_name, "
                    + "p.main_image, p.status, "
                    + "(SELECT MIN(s.price) FROM sku s WHERE s.spu_id = p.id AND s.status = 1) AS price "
                    + "FROM spu p WHERE %s IN (%s)";

    /** 技术键过滤列。 */
    private static final String COLUMN_SPU_PK = "p.id";

    /** 业务键过滤列（{@code spu.spu_id BINARY(16)}）。 */
    private static final String COLUMN_SPU_BUSINESS_ID = "p.spu_id";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<Long, SpuBriefView> findByIds(Collection<Long> spuIds) {
        List<Long> ids = normalize(spuIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SpuBriefView> rows = queryRows(COLUMN_SPU_PK, ids.toArray());
        return rows.stream()
                .filter(row -> row.spuId() != null)
                .collect(Collectors.toMap(SpuBriefView::spuId, row -> row, (first, ignored) -> first));
    }

    @Override
    public Map<String, SpuBriefView> findByBusinessIds(Collection<String> spuBusinessIds) {
        List<byte[]> ids = normalizeBusinessIds(spuBusinessIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SpuBriefView> rows = queryRows(COLUMN_SPU_BUSINESS_ID, ids.toArray());
        return rows.stream()
                .filter(row -> row.spuBusinessId() != null)
                .collect(Collectors.toMap(SpuBriefView::spuBusinessId, row -> row, (first, ignored) -> first));
    }

    /** 单条 SQL 执行（占位符个数与参数一一对应）。 */
    private List<SpuBriefView> queryRows(String filterColumn, Object[] params) {
        String placeholders = "?,".repeat(params.length - 1) + "?";
        return jdbcTemplate.query(SQL_SELECT_BRIEF.formatted(filterColumn, placeholders), ROW_MAPPER, params);
    }

    /** BINARY(16) → 32-hex → BINARY(16) 的宽松解析（非法入参落「未命中」，不放大成 500）。 */
    private List<byte[]> normalizeBusinessIds(Collection<String> spuBusinessIds) {
        if (spuBusinessIds == null || spuBusinessIds.isEmpty()) {
            return List.of();
        }
        List<byte[]> bytes = new ArrayList<>();
        for (String id : spuBusinessIds) {
            byte[] parsed = IdGenerator.toBytesOrNull(id);
            if (parsed != null && bytes.stream().noneMatch(parsed::equals)) {
                bytes.add(parsed);
            }
        }
        return bytes;
    }

    private List<Long> normalize(Collection<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return List.of();
        }
        return spuIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    /** 行映射：技术键（内部关联）+ 业务键（32-hex，出线格式）+ 展示列。 */
    private static final org.springframework.jdbc.core.RowMapper<SpuBriefView> ROW_MAPPER =
            (rs, rowNum) -> new SpuBriefView(
                    rs.getObject("spu_pk", Long.class),
                    IdGenerator.toHexString(rs.getBytes("spu_business_id")),
                    rs.getString("product_name"),
                    rs.getString("main_image"),
                    rs.getObject("price", Integer.class),
                    rs.getObject("status", Integer.class));
}
