package org.icedamericanomall.item.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.SpuVectorClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SpuVectorHitCO;
import org.icedamericanomall.item.adapter.assembler.SpuConverter;
import org.icedamericanomall.item.adapter.assembler.SkuConverter;
import org.icedamericanomall.item.adapter.request.CreateSpuReq;
import org.icedamericanomall.item.adapter.request.UpdateSpuReq;
import org.icedamericanomall.item.adapter.response.SpuVO;
import org.icedamericanomall.item.adapter.response.RecommendSpuVO;
import org.icedamericanomall.item.adapter.response.SkuVO;
import org.icedamericanomall.item.domain.enums.SpuStatusEnum;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.model.spu.SpuSearchCard;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.icedamericanomall.item.app.kg.KgEdgeIngestManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商品编排（应用层）—— 列表/详情装配、发布（聚合+SKU 一并落库）、状态与编辑事务边界、
 * 相似商品推荐组装（P27，ADR-046）。
 *
 * <p>列表项只回填最低价（一次批量聚合，无 N+1）；{@code sort=price} 由数据库按
 * 在售 SKU 最低价排序，保证跨页顺序正确。
 *
 * <p>向量摄取旁路由 {@link SpuRecommendManager} 收口（事务外 afterCommit / 直发）；
 * KG 直建边摄取旁路由 {@link KgEdgeIngestManager} 收口（F15，同款事务纪律）；
 * 相似检索（Feign）与本类其余 Feign 依赖一样只允许出现在应用层。
 * 商品促销标签（F10，P2 详情 V5.11 / P1 列表 V5.13 增量 {@code promotions}）由
 * {@link SpuPromotionManager} 收口（ADR-025 用例族拆分），本类仅装配后一行委托填充。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpuManager {

    /** 相似推荐缺省条数（P27 契约：缺省 5）。 */
    private static final int DEFAULT_SIMILAR_SIZE = 5;
    /** 相似推荐单次上限（P27 契约：≤20 归一）。 */
    private static final int MAX_SIMILAR_SIZE = 20;

    private final SpuRepository spuRepository;
    private final SkuRepository skuRepository;
    private final SpuConverter spuConverter;
    private final SkuConverter skuConverter;
    private final FreightTemplateManager freightTemplateManager;
    private final SkuManager skuManager;
    private final SpuVectorClient spuVectorClient;
    private final SpuRecommendManager spuRecommendManager;
    private final SpuPromotionManager spuPromotionManager;
    private final KgEdgeIngestManager kgEdgeIngestManager;

    /** 商品分页（在售）：sort=price 走最低价排序 SQL，否则销量/广告优先；行级回填促销角标（P1 V5.13）。 */
    public IPage<SpuVO> pageSpus(Long sellerId, Long categoryId, String keyword,
                                         String sort, String order, int page, int size) {
        IPage<Spu> spuPage;
        if ("price".equals(sort)) {
            spuPage = spuRepository.pageOrderByMinPrice(page, size,
                    sellerId, categoryId, keyword,
                    "asc".equals(order) ? "ASC" : "DESC");
        } else {
            spuPage = spuRepository.pageOnSale(sellerId, categoryId, keyword, sort, page, size);
        }
        IPage<SpuVO> voPage = spuPage.convert(spuConverter::toVO);
        fillMinPrice(voPage.getRecords());
        fillPromotions(voPage.getRecords());
        return voPage;
    }

    /**
     * 商品详情（含全部 SKU 与在售最低价、店铺进行中促销标签）；DELETED 视为不存在。
     * <p>促销标签 fail-open（{@link SpuPromotionManager} 内部已兜底空列表），
     * 详情主流程绝不因促销查询失败而失败。读操作无事务，Feign 调用不入事务。
     */
    public SpuVO getSpuDetail(Long id) {
        Spu spu = spuRepository.findById(id);
        if (spu == null || SpuStatusEnum.DELETED.getCode() == spu.getStatus()) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        SpuVO vo = spuConverter.toVO(spu);
        List<Sku> skus = skuRepository.findBySpuId(id);
        List<SkuVO> skuVos = skuConverter.toVOs(skus);
        // ADR-061：SKU 视图带出 SPU/SKU 业务键（一次联表，fail-open），供前端回链与跨接口 join
        skuManager.fillSpuBusinessKey(spu.getSpuId(), skuVos);
        vo.setSkus(skuVos);
        vo.setMinPrice(minPriceOf(skus));
        vo.setPromotions(spuPromotionManager.tagsFor(spu.getSellerId()));
        return vo;
    }

    /** 商品发布（F2：绑定模板归属校验；SKU 经聚合工厂归一热销元数据）。 */
    @Transactional(rollbackFor = Exception.class)
    public void createSpu(Long sellerId, CreateSpuReq req) {
        if (req.getFreightTemplateId() != null) {
            freightTemplateManager.requireBindable(sellerId, req.getFreightTemplateId());
        }
        Spu spu = Spu.publish(sellerId, req.getCategoryId(), req.getFreightTemplateId(),
                req.getName(), req.getMainImage(), req.getDescription(), req.getBrand());
        spu.setSoldCount(0);
        spu.setCommentCount(0);
        spu.setStatus(SpuStatusEnum.ON_SHELF.getCode());
        spu.setPublishTime(LocalDateTime.now());
        Spu saved = spuRepository.save(spu);

        for (CreateSpuReq.SkuReq skuReq : req.getSkus()) {
            Sku sku = Sku.create(spu.getId(), skuReq.getSpec(), skuReq.getPrice(),
                    skuReq.getStock(), skuReq.getImage(), skuReq.getStockType(),
                    skuReq.getIsHot(), skuReq.getHotReason(), skuReq.getSalesTags());
            skuRepository.save(sku);
        }

        // 向量摄取钩子（ADR-046）：CO 取自本次落库聚合，事务提交后才旁路发送——回滚即不摄取
        spuRecommendManager.ingestAfterCommit(saved, onSaleMinPrice(saved.getId()));
        // KG 直建边摄取钩子（F15，I57）：在售商品在事务提交后旁路摄取
        // BELONGS_TO / BRAND_OF / SOLD_BY 三类边；PARENT_OF 类目层级边为低频真相，
        // Phase 1 不做逐次摄取，由 AD64 全量重建统一覆盖（KgEdgeIngestManager 类注释）
        kgEdgeIngestManager.ingestSpuAfterCommit(saved);
    }

    /**
     * 上下架/删除状态变更（**越权防护**：商家仅限自有商品，管理员放行）。
     *
     * <p>为何必须校验：{@code PUT /api/item/product/{id}/status} 的 path 只带商品 id，
     * 若不做归属断言，任一经认证买家即可上下架甚至删除他人商品
     * （status=3 即删除语义）；同文件 {@link #updateSpu} 已有归属断言先例。
     *
     * @param spuId  商品 id
     * @param operatorId 操作人 user_id（商家身份即其 user_id，与 {@code spu.sellerId} 同口径）
     * @param isAdmin    是否平台管理员（放行，对应契约 P5「商家/管理员上下架」）
     * @param status     目标状态（1 上架 / 2 下架 / 3 删除）
     */
    public void updateSpuStatus(Long spuId, Long operatorId, boolean isAdmin, Integer status) {
        Spu spu = spuRepository.findById(spuId);
        if (spu == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!isAdmin && !spu.isOwnedBy(operatorId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该商品");
        }
        spuRepository.updateStatus(spuId, status);
        // KG 直建边口径（F15）：在售持有直建边 / 下架删除清边（I59）——提交后旁路执行，
        // 聚合先同步新状态再摄取，快照与落库一致
        spu.changeStatus(status);
        kgEdgeIngestManager.ingestSpuAfterCommit(spu);
    }

    /** 商家编辑商品（归属校验；F2：运费绑定非空即改，须归属当前商家）。 */
    public void updateSpu(Long spuId, Long sellerId, UpdateSpuReq req) {
        Spu spu = spuRepository.findById(spuId);
        if (spu == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!spu.getSellerId().equals(sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权修改该商品");
        }
        if (req.getFreightTemplateId() != null) {
            freightTemplateManager.requireBindable(sellerId, req.getFreightTemplateId());
        }
        spuRepository.updateEditable(spuId, req.getName(), req.getMainImage(),
                req.getDescription(), req.getBrand(), req.getCategoryId(), req.getFreightTemplateId());

        // 向量摄取钩子（ADR-046）：PATCH 后重读合并态（null 字段保留原值），无活动事务 → 直接旁路发送
        Spu merged = spuRepository.findById(spuId);
        spuRecommendManager.ingestAfterCommit(merged, onSaleMinPrice(spuId));
        // KG 直建边摄取钩子（F15，I57）：合并态幂等覆盖写（改类目/品牌/店铺 → 边随真相更新）
        kgEdgeIngestManager.ingestSpuAfterCommit(merged);
    }

    /** 商品在售 SKU 视图。 */
    public List<SkuVO> listSkus(Long spuId) {
        List<SkuVO> skuVos = skuConverter.toVOs(skuRepository.findOnSaleBySpuId(spuId));
        Spu spu = spuRepository.findById(spuId);
        skuManager.fillSpuBusinessKey(spu == null ? null : spu.getSpuId(), skuVos);
        return skuVos;
    }

    // ==================== 相似商品推荐（P27，ADR-046，纯旁路 fail-open） ====================

    /**
     * 相似商品推荐 —— 向量 kNN 命中 <b>SPU 业务键</b>后批量查本地库，组装新鲜在售商品卡。
     *
     * <p>fail-open（纯旁路）：client 降级已兜底空列表，此处再防御性兜底任意未预期异常
     * （ReviewManager 触达先例口径）；命中商品<b>恒过滤在售</b>并剔除自身/已删除，
     * 按命中顺序（= 相似度降序）组装，不足 size 按实际数量返回——推荐位可空。
     *
     * <p><b>标识口径（ADR-061 §决策 2）</b>：I48 命中是 <b>SPU 业务键</b>（32-hex），
     * 本方法内的比对与取数一律在业务键空间完成；出参 {@code RecommendSpuVO.spuId} 保持
     * <b>技术主键</b>（P27 既有寻址口径，同商品详情路径变量，ADR-061 §后果 R1 已登记）。
     * 业务键 ↔ 技术键解析在同库读模型内<b>一次批量</b>完成（A 案：表结构不动，无 N+1）。
     *
     * @param spuId 锚点商品<b>技术键</b>（路径变量口径）
     * @param size      期望条数（null/非正 → 缺省 5，&gt;20 归一为 20）
     * @return 相似商品卡（相似度降序，{@code spuId} 为技术键）；无命中/降级时为空列表，不报错
     */
    public List<RecommendSpuVO> similarSpus(Long spuId, Integer size) {
        // 锚点：技术键 → 业务键（I48 入参口径，ADR-061 §决策 2）。商品不存在即无「相似商品」可言，
        // 直接返回空列表（fail-open：推荐位可空，不报错、不打远程调用）
        SpuSearchCard anchor = spuId == null ? null : spuRepository.findSearchCardById(spuId);
        if (anchor == null || anchor.spuId() == null) {
            log.debug("相似商品推荐跳过：锚点商品不存在或缺业务键: spuId={}", spuId);
            return List.of();
        }
        List<SpuVectorHitCO> hits = similarSafely(anchor.spuId(), normalizeSize(size));
        if (hits.isEmpty()) {
            return List.of();
        }
        List<String> hitBusinessIds = hits.stream()
                .map(SpuVectorHitCO::getSpuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (hitBusinessIds.isEmpty()) {
            return List.of();
        }
        // 业务键 → 技术键（一次批量 SQL，走 spu_id 唯一索引，无 N+1）
        Map<String, Long> idByBusinessId = spuRepository.idMapByBusinessIds(hitBusinessIds);
        Map<Long, Spu> cardById = onShelfCardsByIds(List.copyOf(idByBusinessId.values()));
        cardById.remove(spuId);
        Map<Long, Integer> minPriceMap = cardById.isEmpty() ? Map.of()
                : skuManager.getMinPriceMapBySpuIds(List.copyOf(cardById.keySet()));
        return hitBusinessIds.stream()
                .map(idByBusinessId::get)
                .filter(Objects::nonNull)
                .filter(cardById::containsKey)
                .map(id -> toRecommendVO(cardById.get(id), minPriceMap.get(id)))
                .toList();
    }

    // ==================== 内部契约回填（I40/I42/I43 消费方） ====================

    public Map<Long, Long> getSellerIdMapBySpuIds(List<Long> spuIds) {
        return spuRepository.sellerIdMapBySpuIds(spuIds);
    }

    public Map<Long, Long> getCategoryIdMapBySpuIds(List<Long> spuIds) {
        return spuRepository.categoryIdMapBySpuIds(spuIds);
    }

    public Map<Long, Long> getFreightTemplateIdMapBySpuIds(List<Long> spuIds) {
        return spuRepository.freightTemplateIdMapBySpuIds(spuIds);
    }

    // ==================== 私有方法 ====================

    /** 一次批量聚合最低价，避免逐商品查询（N+1）。 */
    private void fillMinPrice(List<SpuVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<Long, Integer> minPriceMap = skuManager.getMinPriceMapBySpuIds(
                records.stream().map(SpuVO::getId).toList());
        records.forEach(vo -> vo.setMinPrice(minPriceMap.get(vo.getId())));
    }

    /**
     * 列表促销角标回填（契约 §4.4 P1 V5.13 增量）：与 P2 详情完全同口径
     * （同一 {@code tagsFor}），Caffeine 按 sellerId 缓存 60s——列表 N 行跨 M 个 seller
     * 只打 M 次（同 seller 行共享缓存命中，不打满 marketing）；sellerId 为 null
     * （商品无归属场景）由 {@code tagsFor} 跳过缓存与远程调用；fail-open 空列表不阻断分页。
     */
    private void fillPromotions(List<SpuVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        records.forEach(vo -> vo.setPromotions(spuPromotionManager.tagsFor(vo.getSellerId())));
    }

    /** 商品在售 SKU 的最低价（分），无在售 SKU 时返回 null。 */
    private Integer minPriceOf(List<Sku> skus) {
        return skus.stream()
                .filter(Sku::isOnSale)
                .filter(sku -> sku.getPrice() != null)
                .map(Sku::getPrice)
                .min(Integer::compareTo)
                .orElse(null);
    }

    // ==================== 相似推荐私有装配 ====================

    /** 相似检索防御层：client 降级已兜底空列表，再兜任意未预期异常，推荐位返回空。 */
    private List<SpuVectorHitCO> similarSafely(String spuBusinessId, int size) {
        try {
            return spuVectorClient.similar(spuBusinessId, size);
        } catch (Exception e) {
            log.warn("相似商品检索失败，推荐位返回空: spuId={}, size={}, cause={}",
                    spuBusinessId, size, e.getMessage());
            return List.of();
        }
    }

    /** size 归一（P27 契约：缺省 5、≤20；非正按缺省处理，与服务端 I48 归一口径一致）。 */
    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIMILAR_SIZE;
        }
        return Math.min(size, MAX_SIMILAR_SIZE);
    }

    /**
     * 批量载入命中商品，仅保留在售（下架/删除一并剔除）；id → 聚合。
     * <p>最终组装顺序由命中 id 序列（相似度降序）恢复，本映射无需保序。
     */
    private Map<Long, Spu> onShelfCardsByIds(List<Long> hitIds) {
        return spuRepository.findAllByIds(hitIds).stream()
                .filter(p -> p.getStatus() != null
                        && p.getStatus() == SpuStatusEnum.ON_SHELF.getCode())
                .collect(Collectors.toMap(Spu::getId, p -> p, (a, b) -> a));
    }

    /**
     * 命中商品 → 推荐卡（字段集对齐商品列表卡：展示价 = 在售 SKU 最低价）。
     *
     * <p>{@code spuId} 出 <b>技术主键</b>：P27 推荐卡的寻址语义与商品详情路径变量
     * （{@code GET /api/item/product/{id}}，技术键口径）一致，本批不改其口径（ADR-061 §后果 R1
     * 已登记「路径变量按 A 案保持技术键」）；I48 命中的业务键仅用于本方法内的比对与取数。
     */
    private RecommendSpuVO toRecommendVO(Spu spu, Integer minPrice) {
        RecommendSpuVO vo = new RecommendSpuVO();
        vo.setSpuId(spu.getId());
        vo.setName(spu.getName());
        vo.setMainImage(spu.getMainImage());
        vo.setPrice(minPrice);
        vo.setSoldCount(spu.getSoldCount());
        return vo;
    }

    /** 单商品在售 SKU 最低价（分，对齐商品列表卡 minPrice 口径）；无在售 SKU 时为 null。 */
    private Integer onSaleMinPrice(Long spuId) {
        return skuManager.getMinPriceMapBySpuIds(List.of(spuId)).get(spuId);
    }
}
