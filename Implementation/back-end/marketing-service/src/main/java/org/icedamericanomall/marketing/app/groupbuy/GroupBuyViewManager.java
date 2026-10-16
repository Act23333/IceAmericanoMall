package org.icedamericanomall.marketing.app.groupbuy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.GroupBuyActivityVO;
import org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief;
import org.icedamericanomall.marketing.app.port.FlashSaleSpuBriefQuery;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupBuyActivity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 拼团活动视图装配（应用层用例族，ADR-025）—— 把活动聚合装配为契约 §4.10.4 的
 * {@link GroupBuyActivityVO}，并把 {@code spu_id}/{@code sku_id} 由<b>技术键换为业务键</b>
 * （ADR-061 §决策 2），同时批量回填 SPU 名（供前端列表渲染，减少一次商品查询）。
 *
 * <p><b>为什么单独一类</b>：{@code GroupBuyAdminManager}（管理端 AD54）与
 * {@code GroupBuyParticipationManager}（C 端 M15/M16）共用同一装配口径，
 * 避免同一 VO 在两处口径分叉（与 {@code FlashSaleViewManager} 同构，ADR-025）。
 *
 * <p><b>读模型复用</b>：{@code group_buy_activity} 与 {@code flash_sale} 都只存
 * {@code sku_id} 技术键（A 案：表结构不动），两者用同一条「SKU 技术键 → 业务键 + SPU 名」
 * 只读 SQL 即可 —— 故复用 {@link FlashSaleSpuBriefQuery}，不新造重复的读模型。
 *
 * <p><b>fail-open</b>：读模型异常/行缺失 → {@code spu_id}/{@code sku_id}/{@code spu_name}
 * 为 {@code null}（<b>不回落技术键</b>），活动列表照常返回、不缩行。
 *
 * <p>本类<b>不</b>做 Feign 调用（本地只读查询）——因此 {@code @Transactional} 的
 * 调用方（AD54 创建/AD55 编辑）可安全复用，不触碰 09-constraints「事务内禁止 Feign」红线。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyViewManager {

    private final MarketingConverter marketingConverter;
    private final FlashSaleSpuBriefQuery flashSaleSpuBriefQuery;

    /** 单个活动 → 视图对象（含业务键与 SPU 名回填）。 */
    public GroupBuyActivityVO toView(GroupBuyActivity activity) {
        if (activity == null) {
            return null;
        }
        GroupBuyActivityVO vo = marketingConverter.toVO(activity);
        applyBrief(vo, loadBriefs(List.of(activity)).get(activity.getSkuId()));
        return vo;
    }

    /** 活动列表 → 视图对象列表（整页只打一次读模型查询，无 N+1）。 */
    public List<GroupBuyActivityVO> toViewList(List<GroupBuyActivity> activities) {
        if (activities == null || activities.isEmpty()) {
            return List.of();
        }
        Map<Long, FlashSaleSpuBrief> briefs = loadBriefs(activities);
        return activities.stream().map(activity -> {
            GroupBuyActivityVO vo = marketingConverter.toVO(activity);
            applyBrief(vo, briefs.get(activity.getSkuId()));
            return vo;
        }).toList();
    }

    /**
     * SKU <b>业务键</b> → 商品摘要（AD54 创建端入参解析：业务键 → 技术键）。
     *
     * @param skuBusinessId SKU 业务键（32-hex）；不存在/格式非法时返回 {@code null}
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
            log.warn("拼团创建入参解析失败（SKU 业务键 → 技术键）: skuId={}, cause={}",
                    skuBusinessId, e.getMessage());
            return null;
        }
    }

    /**
     * SKU <b>技术键</b> → 商品摘要（出参方向：本域只存技术键，线格式出业务键）。
     *
     * <p>供 {@code GroupBuyParticipationManager}（I52 回执）与视图装配复用；
     * 与 {@link #findBySkuBusinessId} 共用同一读模型端口（仅入参口径不同）。
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
            log.warn("拼团出参解析失败（SKU 技术键 → 业务键）: skuId={}, cause={}",
                    skuTechnicalId, e.getMessage());
            return null;
        }
    }

    // ==================== 内部方法 ====================

    /** 业务键 + SPU 名回填（缺失时三者保持 null —— 不回落技术键）。 */
    private void applyBrief(GroupBuyActivityVO vo, FlashSaleSpuBrief brief) {
        if (vo == null || brief == null) {
            return;
        }
        vo.setSpuId(brief.spuBusinessId());
        vo.setSkuId(brief.skuBusinessId());
    }

    /** 批量取商品摘要 —— 去重后的 SKU 技术键集合一次查询（fail-open）。 */
    private Map<Long, FlashSaleSpuBrief> loadBriefs(List<GroupBuyActivity> activities) {
        List<Long> skuIds = activities.stream()
                .map(GroupBuyActivity::getSkuId)
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
            log.warn("拼团商品摘要回填失败，业务键置空（fail-open）: skuIds={}, cause={}",
                    skuIds, e.getMessage());
            return Map.of();
        }
    }
}
