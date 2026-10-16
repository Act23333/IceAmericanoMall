package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 搭配推荐卡（P35，契约 §4.4.x V5.16）—— 商品详情页「搭配推荐」位展示对象。
 *
 * <p><b>三级来源口径（US-KG-3）</b>：{@code KG} = 图关系召回（类目 PAIR_WITH 邻居 →
 * 在售商品卡）；{@code FALLBACK} = 无规则命中回退向量相似（P27 管道）；{@code NONE} =
 * 空态（图与向量皆无命中，或商品不在售/降级）。全链路 fail-open：任何环节失效返回
 * {@code NONE} 空列表，绝不报错（对齐 ADR-046 纯旁路定类）。
 *
 * <p>卡片字段集与 P27 相似卡（{@link RecommendSpuVO}）完全同构，前端可复用同一
 * 卡片组件渲染（O6 裁决：与 P27 同页共存，位次随前端设计）。
 */
@Data
public class PairingCardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源：图关系召回。 */
    public static final String SOURCE_KG = "KG";

    /** 来源：向量相似兜底（P27 管道）。 */
    public static final String SOURCE_FALLBACK = "FALLBACK";

    /** 来源：空态（无命中/降级/商品不在售）。 */
    public static final String SOURCE_NONE = "NONE";

    /** 卡片来源（KG / FALLBACK / NONE）。 */
    private String source;

    /** 搭配商品卡列表（空态为空列表，恒非 null）。 */
    private List<RecommendSpuVO> items = List.of();

    /** 图关系命中卡（source=KG）。 */
    public static PairingCardVO kg(List<RecommendSpuVO> items) {
        return of(SOURCE_KG, items);
    }

    /** 向量兜底卡（source=FALLBACK）。 */
    public static PairingCardVO fallback(List<RecommendSpuVO> items) {
        return of(SOURCE_FALLBACK, items);
    }

    /** 空态卡（source=NONE，空列表）。 */
    public static PairingCardVO none() {
        return of(SOURCE_NONE, List.of());
    }

    private static PairingCardVO of(String source, List<RecommendSpuVO> items) {
        PairingCardVO vo = new PairingCardVO();
        vo.setSource(source);
        vo.setItems(items == null ? List.of() : List.copyOf(items));
        return vo;
    }
}
