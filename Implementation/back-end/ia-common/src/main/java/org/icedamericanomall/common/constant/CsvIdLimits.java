package org.icedamericanomall.common.constant;

/**
 * 「承载容量」常量族（ia-common 共享内核，跨模块唯一定义；批次 B7b 新增）。
 *
 * <p><b>为什么要有本类</b>：对外标识改业务键（32-hex）后，四处承载位的容量都被
 * 「每项 33 字符（32 本体 + 1 分隔符）」重新定价 —— 列宽、URL 预算、请求体预算三条
 * 判据得出的上限<b>互不相同</b>，过去按「技术键（BIGINT）时代」的习惯值散落在各处，
 * 一旦有人把「列宽上限 15」借去校验 URL 参数，就会在真机上炸（或反之放行超限值）。
 * 集中定义 + 逐条给出<b>倒推依据</b>，让「这条上限从哪来」可检索。
 *
 * <p><b>三条判据不得互借（硬约束）</b>：
 * <ul>
 *   <li><b>列宽</b> → 列内 CSV 的条数上限（{@link #MAX_CSV_IDS_IN_COLUMN}）；</li>
 *   <li><b>URL 长度预算</b> → 查询参数 CSV 的条数上限（{@link #MAX_COMPARE_SPU_IDS}）；</li>
 *   <li><b>请求体预算</b> → JSON body 数组的条数上限（{@link #MAX_ORDER_ITEM_IDS} /
 *       {@link #MAX_CART_ITEMS}）。</li>
 * </ul>
 * 用错判据是本类缺陷的典型复发形态（依据：{@code project-docs/adr/063-external-channel-number-separation.md}
 * §决策 7「前缀预算」表 + {@code contract/05-API-Specification.md} §1.9⑦-6）。
 *
 * <p><b>覆盖面诚实声明</b>：{@link #MAX_COMPARE_SPU_IDS} 的消费方 P24
 * （{@code GET /api/item/spu/compare}）与 C9（{@code DELETE /api/cart/batch}）在代码中
 * <b>尚不存在</b>（契约先行），故该值目前只有单测断言，<b>不得</b>据此宣称「已有代码校验」。
 */
public final class CsvIdLimits {

    /**
     * 列内 CSV 的条数上限 —— 覆盖 {@code home_config.spu_ids}（{@code VARCHAR(500)}）。
     *
     * <p>倒推：⌊500 / 33⌋ = <b>15</b>（15×32 + 14 个分隔符 = 494 ≤ 500）。
     */
    public static final int MAX_CSV_IDS_IN_COLUMN = 15;

    /**
     * 查询参数 CSV 的条数上限 —— 覆盖 P24 {@code spu_ids}（URL query，逗号分隔）。
     *
     * <p>倒推：URL 长度预算 4×33 = 132 字符 request line，维持现契约「最多 4 个」。
     */
    public static final int MAX_COMPARE_SPU_IDS = 4;

    /**
     * 购物车条目数上限 —— 覆盖「一个用户的购物车行数」（C2 加购的写入侧闸门）。
     *
     * <p><b>这不是性能上限，是下单请求体预算</b>：200 项 × 32-hex ≈ 6.4 KB
     * （技术键时代仅 ≈ 1.2 KB），网关 / Feign / Sentinel 的 body 预算须按业务键形态重算。
     */
    public static final int MAX_CART_ITEMS = 200;

    /**
     * 订单明细标识数组的条数上限 —— 覆盖 T12 {@code ShipOrderReq.sku_ids}、
     * C9 {@code sku_ids} 等 JSON body 数组。
     *
     * <p>判据：≤ {@link #MAX_CART_ITEMS} —— 订单明细的来源是购物车/直购，
     * 明细数不会超过购物车条目上限（超出即意味着绕过了加购闸门）。
     */
    public static final int MAX_ORDER_ITEM_IDS = MAX_CART_ITEMS;

    private CsvIdLimits() {
    }
}
