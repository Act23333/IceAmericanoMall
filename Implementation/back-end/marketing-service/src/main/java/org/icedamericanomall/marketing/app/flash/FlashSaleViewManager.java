package org.icedamericanomall.marketing.app.flash;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
import org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief;
import org.icedamericanomall.marketing.app.port.FlashSaleSpuBriefQuery;
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
 * （先例：{@code SpuPromotionManager} 促销标签 fail-open）。
 *
 * <p>本类<b>不</b>做 Feign 调用（读模型为本地只读查询）——因此 {@code @Transactional} 的
 * 调用方（AD40 创建）可安全复用，不触碰 09-constraints「事务内禁止 Feign」红线。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleViewManager {

    private final MarketingConverter marketingConverter;
    private final FlashSaleSpuBriefQuery flashSaleSpuBriefQuery;

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
        Map<Long, FlashSaleSpuBrief> briefs = loadBriefs(flashSales);
        return flashSales.stream()
                .map(flashSale -> assemble(flashSale, briefs.get(flashSale.getSkuId())))
                .toList();
    }

    // ==================== 内部方法 ====================

    /** 聚合 + 商品摘要 → 视图对象：进度百分比恒有值，商品字段与业务键随摘要有无。 */
    private FlashSaleVO assemble(FlashSale flashSale, FlashSaleSpuBrief brief) {
        FlashSaleVO vo = marketingConverter.toVO(flashSale);
        vo.setProgressPercent(flashSale.progressPercent());
        if (brief != null) {
            // ADR-061 §决策 2：spu_id/sku_id 出业务键 32-hex（不再是技术键）
            vo.setSpuId(brief.spuBusinessId());
            vo.setSkuId(brief.skuBusinessId());
            // ADR-061 §决策 5：SPU 名 / 主图；主图线上名统一 main_image（与 DB 列及其余 VO 同名）
            vo.setSpuName(brief.spuName());
            vo.setMainImage(brief.mainImage());
            vo.setOriginalPrice(brief.originalPrice());
        }
        return vo;
    }

    /**
     * 批量取商品展示摘要 —— 去重后的 SKU ID 集合一次查询。
     *
     * <p>fail-open：异常返回空 Map（商品字段为 null），不向调用方抛。
     */
    private Map<Long, FlashSaleSpuBrief> loadBriefs(List<FlashSale> flashSales) {
        List<Long> skuIds = flashSales.stream()
                .map(FlashSale::getSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, FlashSaleSpuBrief> briefs = flashSaleSpuBriefQuery.findBySkuIds(skuIds);
            return briefs == null ? Map.of() : briefs;
        } catch (Exception e) {
            log.warn("秒杀商品摘要回填失败，商品字段置空（fail-open）: skuIds={}, cause={}",
                    skuIds, e.getMessage());
            return Map.of();
        }
    }

    /**
     * SKU <b>业务键</b> → 商品摘要（ADR-061 §决策 2：AD40 创建端入参收业务键）。
     *
     * <p>创建端需要的是技术键（{@code flash_sale.spu_id}/{@code sku_id} 保持技术键不动 —— A 案），
     * 本方法即那一步解析；顺带校验「SKU 存在且归属该 SPU」，让非法入参在写入前 fail-fast。
     *
     * @param skuBusinessId SKU 业务键（32-hex）
     * @return 摘要；不存在或格式非法时返回 {@code null}（调用方决定异常语义）
     */
    public FlashSaleSpuBrief findBySkuBusinessId(String skuBusinessId) {
        if (skuBusinessId == null) {
            return null;
        }
        try {
            Map<String, FlashSaleSpuBrief> found =
                    flashSaleSpuBriefQuery.findBySkuBusinessIds(List.of(skuBusinessId));
            return found == null ? null : found.get(skuBusinessId);
        } catch (Exception e) {
            log.warn("秒杀创建入参解析失败（SKU 业务键 → 技术键）: skuId={}, cause={}",
                    skuBusinessId, e.getMessage());
            return null;
        }
    }

    /**
     * SKU <b>技术键</b> → 商品摘要（出参方向：本域只存技术键，线格式出业务键）。
     *
     * <p>与 {@link #findBySkuBusinessId} 复用同一读模型端口（不同入参口径），
     * 供 {@code MarketingManager} 在秒杀下单链路把 {@code sku.id} 换为业务键。
     *
     * @param skuTechnicalId SKU 技术键（{@code sku.id}）
     * @return 摘要；不存在时返回 {@code null}
     */
    public FlashSaleSpuBrief findBySkuTechnicalId(Long skuTechnicalId) {
        if (skuTechnicalId == null) {
            return null;
        }
        try {
            Map<Long, FlashSaleSpuBrief> found = flashSaleSpuBriefQuery.findBySkuIds(List.of(skuTechnicalId));
            return found == null ? null : found.get(skuTechnicalId);
        } catch (Exception e) {
            log.warn("秒杀出参解析失败（SKU 技术键 → 业务键）: skuId={}, cause={}",
                    skuTechnicalId, e.getMessage());
            return null;
        }
    }
}
