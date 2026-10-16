package org.icedamericanomall.trade.domain.service;

import org.icedamericanomall.trade.domain.model.order.SkuCurrentState;
import org.icedamericanomall.trade.domain.model.order.SpuCurrentState;

/**
 * 订单项「快照 vs 实时」比对（领域服务，纯函数 —— ADR-061 §决策 3）。
 *
 * <p><b>定位</b>：纯计算，不依赖任何技术组件（无 Spring / Feign / 仓储）；
 * 输入为订单项快照与读模型取回的当前状态，输出比对态与展示口径。
 *
 * <p><b>判定顺序（短路）</b>：
 * <ol>
 *   <li>SKU 业务键<b>查不到行</b>（物理删除）→ {@link CompareState#UNCOMPARABLE}
 *       —— 只显示快照 + 「无法比对」，<b>不得</b>推断为已下架、<b>不得</b>回落技术键；</li>
 *   <li>SPU 状态 = 2（下架）/ 3（删除）→ {@link CompareState#OFF_SHELF}；</li>
 *   <li>SKU 状态 = 0（停售）→ {@link CompareState#DISCONTINUED}；</li>
 *   <li>SKU 库存 = 0 <b>且</b> 该 SKU 属「需校验库存」类型
 *       （{@code LIMITED}/{@code PRESALE}，见 {@link StockAvailabilityPolicy}）→
 *       {@link CompareState#OUT_OF_STOCK}
 *       —— {@code UNLIMITED 不限量} 库存恒 0 <b>不是</b>缺货（漏掉该列会把不限量商品一律标成缺货）；</li>
 *   <li>现价 ≠ 快照价 → {@link CompareState#PRICE_CHANGED}（回显现价，可用同款最低价提示）；</li>
 *   <li>以上皆否 → {@link CompareState#IN_SYNC}（不提示）。</li>
 * </ol>
 *
 * <p><b>只展示，不作业务判定</b>：本类的输出<b>不得</b>用于下单/退款/售后等业务判断。
 */
public final class OrderItemPriceComparator {

    /** SPU 下架状态码。 */
    private static final int SPU_OFF_SHELF = 2;

    /** SPU 已删除状态码。 */
    private static final int SPU_DELETED = 3;

    /** SKU 停售状态码。 */
    private static final int SKU_DISCONTINUED = 0;

    private OrderItemPriceComparator() {
    }

    /**
     * 比对单个订单项。
     *
     * @param snapshotPrice  订单项单价快照（分；{@code order_item.price}）
     * @param snapshotSpuId  订单项 SPU 业务键快照（可为 {@code null}，此时回落 {@code currentSku.spuId()}）
     * @param currentSku     SKU 当前状态；<b>{@code null} = 该业务键查不到行</b>（物理删除）
     * @param currentSpu     SPU 当前状态；可为 {@code null}（SKU 在而 SPU 行缺失属脏数据）
     * @return 比对结果（不可变）
     */
    public static CompareResult compare(Integer snapshotPrice, String snapshotSpuId,
                                        SkuCurrentState currentSku, SpuCurrentState currentSpu) {
        // ① 查不到行 → UNCOMPARABLE（不推断已下架、不回落技术键）
        if (currentSku == null) {
            return new CompareResult(CompareState.UNCOMPARABLE, null, null, null);
        }
        String spuId = currentSku.spuId() != null ? currentSku.spuId() : snapshotSpuId;
        // ② SPU 下架/删除 → OFF_SHELF（快照照常显示）
        if (currentSpu != null && currentSpu.status() != null
                && (currentSpu.status() == SPU_OFF_SHELF || currentSpu.status() == SPU_DELETED)) {
            return new CompareResult(CompareState.OFF_SHELF, currentSku.price(),
                    sameSpuCheaperPrice(currentSpu), spuId);
        }
        // ③ SKU 停售 → DISCONTINUED
        if (currentSku.status() != null && currentSku.status() == SKU_DISCONTINUED) {
            return new CompareResult(CompareState.DISCONTINUED, currentSku.price(),
                    sameSpuCheaperPrice(currentSpu), spuId);
        }
        // ④ 缺货：库存 0 且需校验库存类型（UNLIMITED 恒 0 不算缺货）
        if (isOutOfStock(currentSku)) {
            return new CompareResult(CompareState.OUT_OF_STOCK, currentSku.price(),
                    sameSpuCheaperPrice(currentSpu), spuId);
        }
        // ⑤ 价格变动
        if (priceChanged(snapshotPrice, currentSku.price())) {
            return new CompareResult(CompareState.PRICE_CHANGED, currentSku.price(),
                    sameSpuCheaperPrice(currentSpu), spuId);
        }
        // ⑥ 同步
        return new CompareResult(CompareState.IN_SYNC, currentSku.price(),
                sameSpuCheaperPrice(currentSpu), spuId);
    }

    /**
     * 缺货判定：库存为 0 <b>且</b> 该 SKU 属需校验库存的库存类型。
     *
     * <p>必须以 {@link StockAvailabilityPolicy#requiresStockCheck} 为准 ——
     * 不限量（{@code UNLIMITED}）SKU 的库存恒为 0，把库存 0 直接当缺货会产生<b>假提示</b>。
     */
    private static boolean isOutOfStock(SkuCurrentState currentSku) {
        if (currentSku.stock() == null || currentSku.stock() > 0) {
            return false;
        }
        return StockAvailabilityPolicy.requiresStockCheck(currentSku.stockType());
    }

    /** 价格变动判定：快照价与现价均已知且不相等。任一为空 → 不可判定（按未变动处理）。 */
    private static boolean priceChanged(Integer snapshotPrice, Integer currentPrice) {
        return snapshotPrice != null && currentPrice != null && !snapshotPrice.equals(currentPrice);
    }

    /** 同款现价提示：仅当存在在售 SKU 最低价时给出（否则 {@code null}）。 */
    private static Integer sameSpuCheaperPrice(SpuCurrentState currentSpu) {
        return currentSpu == null ? null : currentSpu.minPrice();
    }

    /**
     * 比对态（{@code compare_state} 候选值）。
     *
     * <p>⚠ <b>线格式字段未定稿</b>（契约 §十二 12.2「比价结果字段」条）：本枚举为
     * 服务端内部口径，<b>不</b>随订单报文出栈；正式对外须先更新契约 + PRD。
     */
    public enum CompareState {

        /** 快照与实时一致，不提示。 */
        IN_SYNC,

        /** 现价与快照价不同，回显现价。 */
        PRICE_CHANGED,

        /** SPU 已下架/已删除，提示「已下架」。 */
        OFF_SHELF,

        /** SKU 已停售，提示「已停售」。 */
        DISCONTINUED,

        /** 需校验库存的 SKU 库存为 0，提示「暂时缺货」。 */
        OUT_OF_STOCK,

        /** SKU 业务键查不到行（物理删除）或读模型降级 —— 只显示快照 + 「无法比对」。 */
        UNCOMPARABLE
    }

    /**
     * 比对结果（展示口径，不可变）。
     *
     * @param state      比对态
     * @param currentPrice 现价（分）；{@code null} = 无可比对现价
     * @param sameSpuMinPrice 同 SPU 在售最低价（分）；用于「同款现价 ¥X」提示，可为 {@code null}
     * @param spuId      比对所用的 SPU 业务键（可回链商品页；可为 {@code null}）
     */
    public record CompareResult(CompareState state, Integer currentPrice,
                                Integer sameSpuMinPrice, String spuId) {
    }
}
