package org.icedamericanomall.item.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.SpuVectorClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SpuDetailCO;
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
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.icedamericanomall.item.domain.port.ShortCodeResolver;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.icedamericanomall.item.app.kg.KgEdgeIngestManager;
import org.springframework.dao.DuplicateKeyException;
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

    /**
     * 短码唯一键冲突的最大尝试次数（含首次）—— ADR-063 §决策 8.3「有界重试 ≤3 次，仍冲突硬失败」。
     */
    private static final int SHORT_CODE_MAX_ATTEMPTS = 3;

    /** 短码唯一索引名（用于从 DB 冲突消息中识别「这次冲突是不是短码」）。 */
    private static final String SHORT_CODE_UNIQUE_KEY = "uk_short_code";

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
    /** 短码解析端口（L1 本地 / L2 Redis / L3 DB 三级缓存；ADR-063 §决策 8）。 */
    private final ShortCodeResolver shortCodeResolver;

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
     *
     * @param spuId SPU <b>业务键</b>（32-hex，来自路径变量 {@code {spuId}}）；形态校验在适配层，
     *              本层负责业务键 → 技术键解析（同库 {@code uk_spu_id}）
     */
    public SpuVO getSpuDetail(String spuId) {
        Spu spu = spuRepository.findByBusinessId(spuId);
        if (spu == null || SpuStatusEnum.DELETED.getCode() == spu.getStatus()) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND);
        }
        SpuVO vo = spuConverter.toVO(spu);
        List<Sku> skus = skuRepository.findBySpuId(spu.getId());
        List<SkuVO> skuVos = skuConverter.toVOs(skus);
        // ADR-061：SKU 视图带出 SPU/SKU 业务键（一次联表，fail-open），供前端回链与跨接口 join
        skuManager.fillSpuBusinessKey(spu.getSpuId(), skuVos);
        vo.setSkus(skuVos);
        vo.setMinPrice(minPriceOf(skus));
        vo.setPromotions(spuPromotionManager.tagsFor(spu.getSellerId()));
        return vo;
    }

    /**
     * 商品详情（<b>按短码</b>，P38 {@code GET /api/item/spu/by-code/{code}}，ADR-063 §决策 7/8）。
     *
     * <p><b>短码只是寻址别名，不是标识</b>：这里把它换成 <b>SPU 业务键</b>，然后<b>完整复用</b>
     * {@link #getSpuDetail(String)} —— 不允许为 P38 另建第二套装配逻辑（两套装配一定会漂移，
     * 而契约要求 P38 与 P2 的 {@code SpuVO} <b>逐字段同形</b>）。
     *
     * <p><b>解析走三级缓存端口</b>（{@link ShortCodeResolver}）：L1 本地 → L2 Redis → L3 DB，
     * 三道防线（负缓存 / single-flight / Redis 故障回落 DB）在适配器内实现，
     * 应用层只关心「有没有」。
     *
     * <p><b>失败语义与 P2 完全一致</b>（契约 §4.4.1 失败语义表）：
     * <ul>
     *   <li>短码不存在 / 从未分配 → {@code NO_RESOURCE_FOUND(1103)} → <b>真 HTTP 404</b>；</li>
     *   <li>短码存在但商品已软删（{@code status=3}） → 同上 404（短码仍算已占用，不回收）；</li>
     *   <li>短码存在但商品已下架（{@code status=2}） → <b>200 + SpuVO</b>（{@code status} 如实回给
     *       落地页，由它渲染「已下架」态）—— 落地页必须能区分「码废了」与「暂时下架」。</li>
     * </ul>
     *
     * @param shortCode 短码（8–10 位小写 hex；形态校验在适配层 {@code ShortCode#require}，
     *                  非法即 400 而非 404）
     * @return 与 P2 同形的商品详情
     * @throws BizException 短码不存在 / 商品已删除（{@code NO_RESOURCE_FOUND}）
     */
    public SpuVO getSpuDetailByCode(String shortCode) {
        String spuBusinessId = shortCodeResolver.resolve(shortCode);
        if (spuBusinessId == null) {
            // 与 P2 同一出口：1103 + 真 404（不是 200 信封；落地页需要真 404 落到宿主错误页）
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND);
        }
        return getSpuDetail(spuBusinessId);
    }

    /**
     * 商品详情 —— <b>内部契约出口</b>（I70 {@code GET /internal/item/spu/{spuId}}，契约 §4.14.5.2）。
     *
     * <p><b>不另建第二套装配</b>：直调 {@link #getSpuDetail(String)}（P2 唯一装配源：聚合装载、
     * SKU 列表、最低价、促销标签），随后只做一次<b>纯投影</b>（{@code SpuVO} → {@code SpuDetailCO}）。
     * 契约对 P38 写过的同一条纪律在此同样适用 —— 两套装配一定会漂移，
     * 而「公开 P2 / 内部 I70 / 短码 P38」三者必须同源同形。
     *
     * <p><b>字段面</b>：{@code SpuDetailCO} 是 P2 装配结果的子集（不含 {@code skus} /
     * {@code min_price} / {@code promotions} / {@code freight_template_id} / {@code comment_count}）；
     * 其 {@code images} / {@code attributes} 两个历史列<b>不由 P2 装配携带</b>，故恒为
     * {@code null} —— 与迁移前「经 Feign 读公开 P2 并解码成 {@code SpuDetailCO}」逐字段一致
     * （那时同样取不到这两个字段），本批不借机新查一次库，避免与 P2 装配分叉。
     *
     * @param spuId SPU <b>业务键</b>（32-hex；形态校验在适配层）
     * @return 商品详情契约对象（内部面不包 {@code Result} 信封，ADR-004）
     * @throws BizException 商品不存在 / 已删除（{@code NO_RESOURCE_FOUND} → 真 404）
     */
    public SpuDetailCO getSpuDetailCO(String spuId) {
        return spuConverter.toDetailCO(getSpuDetail(spuId));
    }

    /** 商品发布（F2：绑定模板归属校验；SKU 经聚合工厂归一热销元数据）。 */
    @Transactional(rollbackFor = Exception.class)
    public void createSpu(Long sellerId, CreateSpuReq req) {
        if (req.getFreightTemplateId() != null) {
            freightTemplateManager.requireBindable(sellerId, req.getFreightTemplateId());
        }
        Spu spu = Spu.publish(sellerId, req.getCategoryId(), req.getFreightTemplateId(),
                req.getSpuName(), req.getMainImage(), req.getDescription(), req.getBrand());
        spu.setSoldCount(0);
        spu.setCommentCount(0);
        spu.setStatus(SpuStatusEnum.ON_SHELF.getCode());
        spu.setPublishTime(LocalDateTime.now());
        // 短码在 publish 已定稿（ADR-063 §决策 8.2）；唯一键冲突时按「有界重试 ≤3 次」换号重试
        Spu saved = saveWithShortCodeRetry(spu);

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
     * 落库并处理<b>短码唯一键冲突</b>（ADR-063 §决策 8.3：有界重试 ≤ {@value #SHORT_CODE_MAX_ATTEMPTS} 次，
     * 仍冲突即硬失败）。
     *
     * <p><b>为什么这里允许重试，而「对外渠道号」只许硬失败</b>：短码在<b>发布之前</b>生成 ——
     * 此刻没有任何物料 / 小程序码引用它，换号<b>无外部可见后果</b>；而渠道号一旦外发就变成
     * 渠道侧幂等键，换号等于让渠道看到两笔交易，只能硬失败。两者口径不同不是不一致。
     *
     * <p><b>共同禁令</b>：碰撞后<b>禁止回落派生值</b>（截取业务键 / 加时间戳 / 自增序号）——
     * 派生值会退化为可枚举、可预测，等于把承载位重新绑回业务键。
     *
     * <p><b>只对短码冲突重试</b>：{@code spu} 表另有 {@code uk_spu_id}，业务键是 UUIDv7 全新生成，
     * 撞键即属异常而非可重试冲突 —— 靠 DB 错误消息里的索引名区分（MySQL 会带上
     * {@code for key 'spu.uk_short_code'}），避免把真正的数据异常套成 3 次无意义重试。
     */
    private Spu saveWithShortCodeRetry(Spu spu) {
        for (int attempt = 1; attempt <= SHORT_CODE_MAX_ATTEMPTS; attempt++) {
            try {
                return spuRepository.save(spu);
            } catch (DuplicateKeyException e) {
                if (!isShortCodeCollision(e) || attempt == SHORT_CODE_MAX_ATTEMPTS) {
                    if (isShortCodeCollision(e)) {
                        log.error("短码分配连续冲突 {} 次，硬失败（不回落到派生值）: spuId={}",
                                SHORT_CODE_MAX_ATTEMPTS, spu.getSpuId());
                        throw new BizException(ErrorCode.INTERNAL_ERROR, "商品发布失败，请重试");
                    }
                    throw e;
                }
                log.warn("短码唯一键冲突，换号重试: attempt={}/{}, spuId={}",
                        attempt, SHORT_CODE_MAX_ATTEMPTS, spu.getSpuId());
                spu.assignNewShortCode();
            }
        }
        // 不可达（循环内已穷尽返回或抛出）；保留以满足编译器的确定返回值
        throw new BizException(ErrorCode.INTERNAL_ERROR, "商品发布失败，请重试");
    }

    /** 唯一键冲突是否来自 {@code uk_short_code}（MySQL 错误消息含索引名）。 */
    private static boolean isShortCodeCollision(DuplicateKeyException e) {
        String message = e.getMessage();
        return message != null && message.contains(SHORT_CODE_UNIQUE_KEY);
    }

    /**
     * 上下架/删除状态变更（**越权防护**：商家仅限自有商品，管理员放行）。
     *
     * <p>为何必须校验：{@code PUT /api/item/spu/{spuId}/status} 的 path 只带商品标识，
     * 若不做归属断言，任一经认证买家即可上下架甚至删除他人商品
     * （status=3 即删除语义）；同文件 {@link #updateSpu} 已有归属断言先例。
     *
     * @param spuId      SPU <b>业务键</b>（32-hex，路径变量口径；§1.9②）
     * @param operatorId 操作人 user_id（商家身份即其 user_id，与 {@code spu.sellerId} 同口径）
     * @param isAdmin    是否平台管理员（放行，对应契约 P5「商家/管理员上下架」）
     * @param status     目标状态（1 上架 / 2 下架 / 3 删除）
     */
    public void updateSpuStatus(String spuId, Long operatorId, boolean isAdmin, Integer status) {
        Spu spu = spuRepository.findByBusinessId(spuId);
        if (spu == null) {
            // 资源不存在 → 1103 + 真 404（09 §三·六 四类划分：不是「业务规则拒绝」，不给 200 信封）
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND);
        }
        if (!isAdmin && !spu.isOwnedBy(operatorId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该商品");
        }
        spuRepository.updateStatus(spu.getId(), status);
        // KG 直建边口径（F15）：在售持有直建边 / 下架删除清边（I59）——提交后旁路执行，
        // 聚合先同步新状态再摄取，快照与落库一致
        spu.changeStatus(status);
        kgEdgeIngestManager.ingestSpuAfterCommit(spu);
    }

    /**
     * 商家编辑商品（归属校验；F2：运费绑定非空即改，须归属当前商家）。
     *
     * @param spuId    SPU <b>业务键</b>（32-hex，路径变量口径）
     * @param sellerId 当前登录商家（{@code spu.seller_id} 同口径）
     */
    public void updateSpu(String spuId, Long sellerId, UpdateSpuReq req) {
        Spu spu = spuRepository.findByBusinessId(spuId);
        if (spu == null) {
            // 同上：编辑一个不存在的商品 = 资源不存在（1103 / 404），不是业务规则拒绝
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND);
        }
        if (!spu.getSellerId().equals(sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权修改该商品");
        }
        if (req.getFreightTemplateId() != null) {
            freightTemplateManager.requireBindable(sellerId, req.getFreightTemplateId());
        }
        Long spuTechnicalId = spu.getId();
        spuRepository.updateEditable(spuTechnicalId, req.getSpuName(), req.getMainImage(),
                req.getDescription(), req.getBrand(), req.getCategoryId(), req.getFreightTemplateId());

        // 向量摄取钩子（ADR-046）：PATCH 后重读合并态（null 字段保留原值），无活动事务 → 直接旁路发送
        Spu merged = spuRepository.findById(spuTechnicalId);
        spuRecommendManager.ingestAfterCommit(merged, onSaleMinPrice(spuTechnicalId));
        // KG 直建边摄取钩子（F15，I57）：合并态幂等覆盖写（改类目/品牌/店铺 → 边随真相更新）
        kgEdgeIngestManager.ingestSpuAfterCommit(merged);
    }

    /**
     * 商品在售 SKU 视图。
     *
     * @param spuId SPU <b>业务键</b>（32-hex）；商品不存在/已删除 → 空列表（与既有
     *              「未知 id → 空列表」语义一致，不放大成异常）
     */
    public List<SkuVO> listSkus(String spuId) {
        Spu spu = spuRepository.findByBusinessId(spuId);
        if (spu == null) {
            return List.of();
        }
        List<SkuVO> skuVos = skuConverter.toVOs(skuRepository.findOnSaleBySpuId(spu.getId()));
        skuManager.fillSpuBusinessKey(spu.getSpuId(), skuVos);
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
     * <p><b>标识口径（ADR-062 §决策 2/3）</b>：入参锚点与出参 {@code RecommendSpuVO.spuId}
     * 一律 <b>SPU 业务键</b> 32-hex（外部寻址口径统一，路径变量/卡片回链同型同义）；
     * 本方法内的比对与取数在业务键空间完成，业务键 ↔ 技术键解析在同库读模型内
     * <b>一次批量</b>完成（A 案：表结构不动，无 N+1）。
     *
     * @param spuId 锚点 SPU <b>业务键</b>（32-hex，路径变量口径；形态校验在适配层）
     * @param size  期望条数（null/非正 → 缺省 5，&gt;20 归一为 20）
     * @return 相似商品卡（相似度降序，{@code spuId} 为业务键）；无命中/降级时为空列表，不报错
     */
    public List<RecommendSpuVO> similarSpus(String spuId, Integer size) {
        // 锚点：业务键 → 本域聚合（同库 uk_spu_id）。商品不存在即无「相似商品」可言，
        // 直接返回空列表（fail-open：推荐位可空，不报错、不打远程调用）
        Spu anchor = spuId == null ? null : spuRepository.findByBusinessId(spuId);
        if (anchor == null || anchor.getSpuId() == null) {
            log.debug("相似商品推荐跳过：锚点商品不存在或缺业务键: spuId={}", spuId);
            return List.of();
        }
        List<SpuVectorHitCO> hits = similarSafely(anchor.getSpuId(), normalizeSize(size));
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
        // 排除自身：命中列表按业务键返回，锚点已在上面解析为聚合，按技术键剔除即可
        cardById.remove(anchor.getId());
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
     * <p>{@code spuId} 出 <b>SPU 业务键</b>（32-hex）：卡内回链与详情路径变量
     * （{@code GET /api/item/spu/{spuId}}）同型同义（ADR-062 §决策 3、契约 §1.9②）；
     * 此前为技术键的口径并存已按 B4 裁决收敛（ADR-062 §后果 E3）。
     */
    private RecommendSpuVO toRecommendVO(Spu spu, Integer minPrice) {
        RecommendSpuVO vo = new RecommendSpuVO();
        vo.setSpuId(spu.getSpuId());
        vo.setSpuName(spu.getName());
        vo.setMainImage(spu.getMainImage());
        vo.setPrice(minPrice);
        vo.setSoldCount(spu.getSoldCount());
        return vo;
    }

    /** 单商品在售 SKU 最低价（分，对齐商品列表卡 minPrice 口径）；无在售 SKU 时为 null。 */
    private Integer onSaleMinPrice(Long spuTechnicalId) {
        return skuManager.getMinPriceMapBySpuIds(List.of(spuTechnicalId)).get(spuTechnicalId);
    }
}
