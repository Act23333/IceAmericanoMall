package org.icedamericanomall.marketing.app.flash;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
import org.icedamericanomall.marketing.app.dto.FlashSaleProductBrief;
import org.icedamericanomall.marketing.app.port.FlashSaleProductBriefQuery;
import org.icedamericanomall.marketing.domain.model.flash.FlashSale;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 秒杀展示视图装配（应用层用例族，ADR-025）—— 把场次聚合装配为契约 §4.10.2 的
 * {@link FlashSaleVO}，并批量回填商品展示字段。
 *
 * <p><b>为什么单独一类</b>：{@code FlashSaleManager} 只做单聚合内的领域操作（Redis 预扣/统计），
 * 跨聚合读模型回填属应用层装配职责；抽成独立组件让「M8 列表」与「AD40 创建回执」
 * 共用同一份装配逻辑（同一 VO 不会两处口径分叉）。
 *
 * <p><b>回填字段</b>（契约声明、此前未投影）：{@code product_name} / {@code product_image} /
 * {@code original_price}；另装配派生字段 {@code progress_percent}（聚合自身字段推导，
 * 见 {@link FlashSale#progressPercent()}）。
 *
 * <p><b>fail-open（附加展示字段的既有取向）</b>：数据源缺失/异常 → 三个商品字段为 null，
 * 秒杀列表与创建回执照常返回；绝不因补展示字段让主接口变脆
 * （先例：{@code ProductPromotionManager} 促销标签 fail-open）。
 *
 * <p>本类<b>不</b>做 Feign 调用（读模型为本地只读查询）——因此 {@code @Transactional} 的
 * 调用方（AD40 创建）可安全复用，不触碰 09-constraints「事务内禁止 Feign」红线。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleViewManager {

    private final MarketingConverter marketingConverter;
    private final FlashSaleProductBriefQuery flashSaleProductBriefQuery;

    /**
     * 单个场次 → 视图对象（含商品字段回填与进度百分比）。
     *
     * @param flashSale 场次聚合；{@code null} 返回 {@code null}
     * @return 视图对象（商品摘要缺失时三字段为 null）
     */
    public FlashSaleVO toView(FlashSale flashSale) {
        if (flashSale == null) {
            return null;
        }
        return assemble(flashSale, loadBriefs(List.of(flashSale)).get(flashSale.getSkuId()));
    }

    /**
     * 场次列表 → 视图对象列表（整页只打一次商品摘要查询，无 N+1）。
     *
     * @param flashSales 场次聚合列表（可空）
     * @return 视图对象列表；空入参返回空列表
     */
    public List<FlashSaleVO> toViewList(List<FlashSale> flashSales) {
        if (flashSales == null || flashSales.isEmpty()) {
            return List.of();
        }
        Map<Long, FlashSaleProductBrief> briefs = loadBriefs(flashSales);
        return flashSales.stream()
                .map(flashSale -> assemble(flashSale, briefs.get(flashSale.getSkuId())))
                .toList();
    }

    // ==================== 内部方法 ====================

    /** 聚合 + 商品摘要 → 视图对象：进度百分比恒有值，商品三字段随摘要有无。 */
    private FlashSaleVO assemble(FlashSale flashSale, FlashSaleProductBrief brief) {
        FlashSaleVO vo = marketingConverter.toVO(flashSale);
        vo.setProgressPercent(flashSale.progressPercent());
        if (brief != null) {
            vo.setProductName(brief.productName());
            vo.setProductImage(brief.productImage());
            vo.setOriginalPrice(brief.originalPrice());
        }
        return vo;
    }

    /**
     * 批量取商品展示摘要 —— 去重后的 SKU ID 集合一次查询。
     *
     * <p>fail-open：异常返回空 Map（三个商品字段为 null），不向调用方抛。
     */
    private Map<Long, FlashSaleProductBrief> loadBriefs(List<FlashSale> flashSales) {
        List<Long> skuIds = flashSales.stream()
                .map(FlashSale::getSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, FlashSaleProductBrief> briefs = flashSaleProductBriefQuery.findBySkuIds(skuIds);
            return briefs == null ? Map.of() : briefs;
        } catch (Exception e) {
            log.warn("秒杀商品摘要回填失败，商品字段置空（fail-open）: skuIds={}, cause={}",
                    skuIds, e.getMessage());
            return Map.of();
        }
    }
}
