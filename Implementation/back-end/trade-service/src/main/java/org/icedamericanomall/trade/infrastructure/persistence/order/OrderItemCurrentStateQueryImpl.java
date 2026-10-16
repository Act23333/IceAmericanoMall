package org.icedamericanomall.trade.infrastructure.persistence.order;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.trade.app.port.OrderItemCurrentStateQuery;
import org.icedamericanomall.trade.domain.model.order.SkuCurrentState;
import org.icedamericanomall.trade.domain.model.order.SpuCurrentState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单项当前状态查询实现（基础设施层）—— 只读联表读模型（<b>整页一次 SQL</b>）。
 *
 * <p><b>边界（ADR-057 / ADR-061 §决策 3）</b>：本实现只读 {@code sku}/{@code spu}
 * （同一 schema），不写任何被读域数据；不得外泄到适配层；<b>仅作展示</b>，
 * 不作下单/退款/售后的业务判定依据。
 *
 * <p><b>单条 SQL / 无 N+1</b>：两种入参（SKU 业务键 / SPU 业务键）各自一条 {@code IN (...)} 查询；
 * 最低价用相关子查询（{@code MIN(price) WHERE status=1}），不逐行回表。
 *
 * <p><b>业务键入参 → {@code byte[]}</b>：{@code BINARY(16)} 列与 {@code String} 比较会
 * <b>静默查空</b>（见 {@code IdGenerator} 说明），故一律经 {@link IdGenerator#toBytesOrNull}
 * 转 {@code byte[]}；非法入参返回 {@code null} → 该元素被丢弃（落「未命中」，不放大成 500）。
 *
 * <p><b>异常语义</b>：本类<b>不吞异常</b>（真实故障须在调用侧可见）；fail-open 由
 * {@code OrderCurrentStateEnricher} 统一兜住（异常 → 空 Map + WARN，明细不缩行）。
 *
 * <p><b>只读</b>：仅 SELECT；参数一律走占位符（ADR-050 SQL 字面量禁令）。
 */
@Repository
@RequiredArgsConstructor
public class OrderItemCurrentStateQueryImpl implements OrderItemCurrentStateQuery {

    /**
     * SKU 当前状态（含「同 SPU 在售最低价」，用于「同款现价」提示）。
     *
     * <p>{@code stock_type} 是缺货判定的必需列 —— 不限量 SKU 库存恒 0 不是缺货（ADR-061 §决策 3）。
     */
    private static final String SQL_SELECT_SKU_STATE =
            "SELECT s.sku_id AS sku_business_id, p.spu_id AS spu_business_id, s.price, s.stock, "
                    + "s.stock_type, s.status, "
                    + "(SELECT MIN(s2.price) FROM sku s2 WHERE s2.spu_id = s.spu_id AND s2.status = 1) AS min_price "
                    + "FROM sku s LEFT JOIN spu p ON s.spu_id = p.id "
                    + "WHERE s.sku_id IN (%s)";

    /**
     * SPU 当前状态（含在售 SKU 最低价）。
     *
     * <p>{@code LEFT JOIN} + 相关子查询（不是 {@code INNER JOIN sku}）：无在售 SKU 的 SPU
     * 仍需返回行（{@code min_price = null}），否则「SPU 已下架」会被误判成「SKU 已删除」。
     */
    private static final String SQL_SELECT_SPU_STATE =
            "SELECT p.spu_id AS spu_business_id, p.status, "
                    + "(SELECT MIN(s.price) FROM sku s WHERE s.spu_id = p.id AND s.status = 1) AS min_price "
                    + "FROM spu p WHERE p.spu_id IN (%s)";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, SkuCurrentState> findSkuStates(Collection<String> skuBusinessIds) {
        List<byte[]> ids = normalizeBusinessIds(skuBusinessIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, SkuCurrentState> result = new LinkedHashMap<>();
        for (SkuCurrentState row : queryRows(SQL_SELECT_SKU_STATE, ids.toArray(), SKU_ROW_MAPPER)) {
            if (row.skuId() != null && row.spuId() != null) {
                result.putIfAbsent(row.skuId(), row);
            }
        }
        return result;
    }

    @Override
    public Map<String, SpuCurrentState> findSpuStates(Collection<String> spuBusinessIds) {
        List<byte[]> ids = normalizeBusinessIds(spuBusinessIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, SpuCurrentState> result = new LinkedHashMap<>();
        for (SpuCurrentState row : queryRows(SQL_SELECT_SPU_STATE, ids.toArray(), SPU_ROW_MAPPER)) {
            if (row.spuId() != null) {
                result.putIfAbsent(row.spuId(), row);
            }
        }
        return result;
    }

    /** 单条 SQL 执行（占位符个数与参数一一对应）。 */
    private <T> List<T> queryRows(String sqlTemplate, Object[] params, RowMapper<T> rowMapper) {
        String placeholders = "?,".repeat(params.length - 1) + "?";
        return jdbcTemplate.query(sqlTemplate.formatted(placeholders), rowMapper, params);
    }

    /** BINARY(16) → 32-hex → BINARY(16) 的宽松解析（非法入参落「未命中」，不放大成 500）。 */
    private List<byte[]> normalizeBusinessIds(Collection<String> businessIds) {
        if (businessIds == null || businessIds.isEmpty()) {
            return List.of();
        }
        List<byte[]> bytes = new ArrayList<>();
        for (String id : businessIds) {
            byte[] parsed = IdGenerator.toBytesOrNull(id);
            if (parsed != null && bytes.stream().noneMatch(parsed::equals)) {
                bytes.add(parsed);
            }
        }
        return bytes;
    }

    /**
     * SKU 行映射。
     *
     * <p>「同 SPU 在售最低价」在 SKU 行上另存一列（不入 record）—— 通过 {@code minPrice}
     * 组装进 {@link SpuCurrentState}（调用方按 SPU 聚合），故此处仅取 SKU 自身六列。
     */
    private static final RowMapper<SkuCurrentState> SKU_ROW_MAPPER = (rs, rowNum) -> new SkuCurrentState(
            IdGenerator.toHexString(rs.getBytes("sku_business_id")),
            IdGenerator.toHexString(rs.getBytes("spu_business_id")),
            rs.getObject("price", Integer.class),
            rs.getObject("stock", Integer.class),
            rs.getObject("stock_type", Integer.class),
            rs.getObject("status", Integer.class));

    /** SPU 行映射（{@code min_price} = 在售 SKU 最低价，无在售 SKU 时为 null）。 */
    private static final RowMapper<SpuCurrentState> SPU_ROW_MAPPER = (rs, rowNum) -> new SpuCurrentState(
            IdGenerator.toHexString(rs.getBytes("spu_business_id")),
            rs.getObject("status", Integer.class),
            rs.getObject("min_price", Integer.class));
}
