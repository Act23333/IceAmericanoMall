package org.icedamericanomall.marketing.domain.service;

import org.icedamericanomall.marketing.domain.enums.ScopeTypeEnum;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 优惠券适用范围判定（纯领域服务，零框架依赖）—— V4.3 {@code coupon.scope_type} /
 * {@code coupon.scope_values} 的唯一解释口径，供下单核销路径（I22 {@code /internal/coupon/use}）复用。
 *
 * <p><b>适用范围口径</b>（{@link ScopeTypeEnum}）：
 * <ul>
 *   <li>{@code 1 ALL}：全场通用，恒放行；</li>
 *   <li>{@code 2 CATEGORY}：订单品类 ID 与券的 {@code scopeValues} 需有交集；</li>
 *   <li>{@code 3 PRODUCT}：订单商品 ID 与券的 {@code scopeValues} 需有交集；</li>
 *   <li>{@code scopeType} 为 {@code null} 或 {@code scopeValues} 为空 → 视为 {@code ALL} 放行
 *       （存量数据/历史调用方向后兼容，防御 null）。</li>
 * </ul>
 *
 * <p><b>入参形态</b>：{@code scopeValues} 是 DB JSON 列的原样字符串 ——
 * 兼容 {@code ["1","2"]}（字符串元素）与 {@code [1,2]}（数字元素）两种形态，由
 * {@link #parseIds(String)} 统一归一为去引号去空白的 ID 集合；订单侧入参是<b>逗号分隔</b>
 * 的 ID 串（逐项比较，不做数值语义解析）。
 *
 * <p><b>为何手写解析而不引 Jackson</b>：域层零框架依赖（ADR-038），且此处只需「数字 ID 集合」
 * 一种最简形态；引入 {@code TypeHandler}/Jackson 会把 JSON 技术细节下沉到持久化层，
 * 与「不新增 TypeHandler」的既定口径冲突。
 */
public final class CouponScope {

    private CouponScope() {
    }

    /**
     * 判断券是否适用于本单商品/品类。
     *
     * @param scopeType   券的适用范围类型（{@code null} = ALL）
     * @param scopeValues 券的适用范围 ID 列表（JSON 字符串；{@code null}/空 = 不限）
     * @param spuIds  订单商品 ID 串（逗号分隔；未传 = 未知）
     * @param categoryIds 订单品类 ID 串（逗号分隔；未传 = 未知）
     * @return {@code true} 表示放行；有限定范围但订单侧无交集（含订单侧未传）时返回 {@code false}
     */
    public static boolean accepts(Integer scopeType, String scopeValues, String spuIds, String categoryIds) {
        if (scopeType == null) {
            return true;
        }
        int type = scopeType;
        if (type == ScopeTypeEnum.ALL.getCode()) {
            return true;
        }
        Set<String> limits = parseIds(scopeValues);
        if (limits.isEmpty()) {
            // 有限定类型但未配置 ID 列表 = 配置残缺，按「不限」放行（与存量默认值 1 的行为一致）
            return true;
        }
        if (type == ScopeTypeEnum.CATEGORY.getCode()) {
            return intersects(limits, splitIds(categoryIds));
        }
        if (type == ScopeTypeEnum.PRODUCT.getCode()) {
            return intersects(limits, splitIds(spuIds));
        }
        // 未知 scopeType（脏数据）→ 视为全场，避免把用户券误判为不可用
        return true;
    }

    /**
     * 解析 {@code scope_values} JSON 数组字符串为 ID 集合（形态容错）。
     *
     * <p>只做分隔符级解析，不引入 JSON 库：{@code ["1","2"]} / {@code [1,2]} / {@code [1, 2]} 均归一为
     * {@code {"1","2"}}；另容忍「被当成 JSON 标量存储的数组字符串」（外层多一层引号，见
     * {@link #unwrap}）；{@code null}/空白/非数组内容返回空集合。
     *
     * @param scopeValues JSON 数组字符串，允许 {@code null}
     * @return 去重且保持顺序的 ID 集合（无则空集合）
     */
    public static Set<String> parseIds(String scopeValues) {
        if (scopeValues == null || scopeValues.isBlank()) {
            return Set.of();
        }
        String body = unwrap(scopeValues.trim());
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        return new LinkedHashSet<>(Arrays.stream(unescape(body).split(","))
                .map(CouponScope::stripQuotes)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    /**
     * {@code scope_values} 存储形态（DB JSON 数组）→ 对外形态（CSV 串）—— ADR-058 §2 同型同义。
     *
     * <p><b>为何归一渲染</b>：{@code coupon.scope_values} 是 {@code JSON} 列，原样回吐会让
     * 客户端拿到 {@code ["1001","1002"]}；契约 §4.10.1 与 {@code UserCouponVO} 均声明
     * 「对外 CSV 串 / 存储 JSON 数组」，两个 VO 必须同型同义。
     *
     * <p><b>ALL 特例</b>：{@code scopeType} 为 {@code null}（缺省语义 = ALL）或
     * {@link ScopeTypeEnum#ALL} 时对外恒为 {@code null}（契约样例的合法形态），
     * 避免前端把「全场券」误当有限定范围。
     *
     * @param scopeType   适用范围类型；{@code null} = 按 ALL 处理
     * @param scopeValues DB JSON 数组字符串（可空）
     * @return CSV 串（保持库内顺序、去重）；ALL 或无数值时返回 {@code null}
     */
    public static String toCsv(Integer scopeType, String scopeValues) {
        if (scopeType == null || scopeType == ScopeTypeEnum.ALL.getCode()) {
            return null;
        }
        Set<String> ids = parseIds(scopeValues);
        return ids.isEmpty() ? null : String.join(",", ids);
    }

    /**
     * 逗号分隔 ID 串 → ID 集合。
     *
     * @param csv 逗号分隔串，允许 {@code null}
     * @return 去重且保持顺序的 ID 集合（无则空集合）
     */
    public static Set<String> splitIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.stream(csv.split(","))
                .map(CouponScope::stripQuotes)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    /** 两侧集合是否有交集（空集合恒不相交 —— 「未传 ids」不等于「不限」）。 */
    private static boolean intersects(Set<String> limits, Set<String> orderIds) {
        return !orderIds.isEmpty() && orderIds.stream().anyMatch(limits::contains);
    }

    /**
     * 剥掉最外层成对的 JSON 引号 —— 兼容「数组被当作 JSON 标量写入」的形态
     * （某些方言的 {@code JSON} 列可能返回 {@code "[\"1\",\"2\"]"}）。
     */
    private static String unwrap(String raw) {
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1).trim();
        }
        return raw;
    }

    /** 去掉 JSON 转义反斜杠（配合 {@link #unwrap} 处理被转义的内层引号）。 */
    private static String unescape(String raw) {
        return raw.indexOf('\\') < 0 ? raw : raw.replace("\\", "");
    }

    /** 去引号（相容 JSON 字符串元素）与首尾空白。 */
    private static String stripQuotes(String raw) {
        return raw.trim().replace("\"", "").trim();
    }
}
