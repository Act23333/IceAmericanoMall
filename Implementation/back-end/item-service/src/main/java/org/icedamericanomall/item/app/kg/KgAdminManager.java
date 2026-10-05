package org.icedamericanomall.item.app.kg;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.adapter.request.PairingRuleReq;
import org.icedamericanomall.item.adapter.request.PairingRuleUpdateReq;
import org.icedamericanomall.item.adapter.response.PairingRuleVO;
import org.icedamericanomall.item.domain.model.category.Category;
import org.icedamericanomall.item.domain.model.kg.KgSchema;
import org.icedamericanomall.item.domain.model.kg.PairingRule;
import org.icedamericanomall.item.domain.model.product.Product;
import org.icedamericanomall.item.domain.repository.CategoryRepository;
import org.icedamericanomall.item.domain.repository.PairingRuleRepository;
import org.icedamericanomall.item.domain.repository.ProductRepository;
import org.icedamericanomall.client.KgClient;
import org.icedamericanomall.dto.KgEdgeItemCO;
import org.icedamericanomall.dto.KgEdgeUpsertCO;
import org.icedamericanomall.dto.KgNeighborCO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识图谱治理编排（应用层 · 平台后台，F15 AD61-AD65，契约 §4.13.8f）。
 *
 * <p>与 {@link KgPairingManager}（P35 消费面）按用例族拆分（ADR-025）。AD62/AD63 为
 * 纯本地 DB 操作，事务边界在本层；AD64 重建含逐 src Feign 写入，<b>无事务</b>
 * （硬约束：Feign 绝不入事务），fail-open 逐 src 继续；AD65 为只读检索透传（无事务）。
 *
 * <p><b>运营口径</b>：规则落库即同步边投影（afterCommit，D3 三轨之 C 轨）——创建与
 * 启停即时生效；AD64 全量重建为兜底轨（US-KG-8），按真相源（源表列 + 规则表）逐 src
 * 幂等覆盖 {@code kg_edges} 投影，可重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KgAdminManager {

    /** 重建逐批扫描页大小（商品真相源分页遍历粒度）。 */
    private static final int REBUILD_PAGE_SIZE = 500;

    /** 邻居查询缺省跳数（AD65 契约：depth 缺省 1）。 */
    private static final int DEFAULT_NEIGHBOR_DEPTH = 1;

    /** 邻居查询最小跳数（低于该值按缺省处理，与 search 侧 {@code normalizeDepth} 同款归一）。 */
    private static final int MIN_NEIGHBOR_DEPTH = 1;

    /** 邻居查询能力边界（PRD §16.4：≤2 跳，超出拒绝为 1101）。 */
    private static final int MAX_NEIGHBOR_DEPTH = 2;

    private final PairingRuleRepository pairingRuleRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final KgEdgeIngestManager kgEdgeIngestManager;
    private final KgClient kgClient;

    /**
     * AD61：规则分页（src/dst/status 三维可选过滤）。
     *
     * @param srcCategoryId 源类目过滤（null = 不过滤）
     * @param dstCategoryId 目标类目过滤（null = 不过滤）
     * @param status        状态过滤（null = 不过滤）
     * @param pageNum       页码（1 起，适配层已归一）
     * @param pageSize      页大小（适配层已归一）
     * @return 规则分页（管理端视图）
     */
    public IPage<PairingRuleVO> page(Long srcCategoryId, Long dstCategoryId, Integer status,
                                     int pageNum, int pageSize) {
        return pairingRuleRepository.page(srcCategoryId, dstCategoryId, status, pageNum, pageSize)
                .convert(this::toVO);
    }

    /**
     * AD62：创建搭配规则（类目存在性校验 + 业务唯一键防重 + 落库后同步边投影）。
     *
     * <p>唯一性采用「先查后插 + {@code uk_src_dst} 唯一键兜底」双路径（TaskAdminManager
     * 先例），统一回落 {@code PARAM_ERROR(1101)} / 400；投影同步走 afterCommit
     * （{@link KgEdgeIngestManager}），事务回滚即不投影。
     *
     * @param req 创建请求（必填校验已在适配层完成）
     * @throws BizException 源/目标类目不存在（1101，悬空不产生边）；
     *                      规则已存在（1101，先查命中或并发撞唯一键）
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(PairingRuleReq req) {
        requireCategoryExists(req.getSrcCategoryId(), "源类目");
        requireCategoryExists(req.getDstCategoryId(), "目标类目");
        ensurePairAvailable(req.getSrcCategoryId(), req.getDstCategoryId());
        PairingRule rule = PairingRule.create(req.getSrcCategoryId(), req.getDstCategoryId(),
                req.getWeight(), req.getRemark());
        try {
            pairingRuleRepository.save(rule);
        } catch (DuplicateKeyException e) {
            // 先查后插的竞态兜底（uk_src_dst 唯一键）：并发创建同一规则 → 统一 400
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "搭配规则已存在: " + rule.getSrcCategoryId() + "->" + rule.getDstCategoryId());
        }
        kgEdgeIngestManager.syncPairingEdgesAfterCommit(rule.getSrcCategoryId(),
                pairingRuleRepository.findAllBySrc(rule.getSrcCategoryId()));
    }

    /**
     * AD63：编辑搭配规则（null-safe 局部更新 weight / remark / status，启停即时生效）。
     *
     * @param id  规则技术主键（契约寻址口径）
     * @param req 编辑请求（src/dst 不可改）
     * @throws BizException 规则不存在（{@link ErrorCode#NO_RESOURCE_FOUND}，404）
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PairingRuleUpdateReq req) {
        PairingRule rule = requireById(id);
        rule.applyPatch(req.getWeight(), req.getRemark(), req.getStatus());
        pairingRuleRepository.update(rule);
        // 启停即时生效：落库后按最新规则快照同步 PAIR_WITH 投影（停用边以 status=0 写入）
        kgEdgeIngestManager.syncPairingEdgesAfterCommit(rule.getSrcCategoryId(),
                pairingRuleRepository.findAllBySrc(rule.getSrcCategoryId()));
    }

    /**
     * AD64：全量重建边投影（US-KG-8 兜底轨）——按真相源（源表列 + 规则表）逐 src
     * 幂等覆盖 {@code kg_edges}，可重试；返回成功写入边数。
     *
     * <p>重建四路：① 在售商品 → 直建三边；② 下架/删除商品 → 清边（投影镜像增量摄取口径）；
     * ③ 类目 PARENT_OF（Phase 1 唯一覆盖路径，低频）；④ 规则 PAIR_WITH（含停用，status 原样）。
     * 逐 src Feign 写入 fail-open（哨兵/异常按 0 边计、WARN 继续），无事务。
     *
     * @return 成功写入边数（降级/失败部分不计入，可重跑补齐）
     */
    public int rebuild() {
        int written = rebuildProductEdges() + rebuildCategoryParentEdges() + rebuildPairingRuleEdges();
        log.info("[KG_REBUILD] [重建边投影] [written={}] [fail-open 逐 src 继续]", written);
        return written;
    }

    /**
     * AD65：关系邻居表格化核查视图（US-KG-5，契约 §4.13.8f V5.16.1）——包装 I58 检索面
     * （{@link KgClient#neighbors}），管理端按 src 核查图数据（关系类型/目标/权重/评分）。
     *
     * <p><b>参数预检口径（选 Manager 预检并注明）</b>：srcType/srcId 必填（缺失 → 1101，
     * 对齐 search 侧 {@code KgEdgeManager} 同款守卫）；depth 归一（null/非正 → 缺省 1）与
     * {@code >2} 拒绝（1101，图查询能力边界 ≤2 跳，PRD §16.4）在 item 侧先行——search 侧
     * 保留同款拒绝作为二道防线。
     *
     * <p><b>fail-open</b>：ES 降级由 ia-api {@code KgClientFallback} 承担（返回空列表），
     * 本方法原样透传；本方法无事务（只读 Feign，事务纪律硬约束）。
     *
     * @param srcType 源节点类型（必填）
     * @param srcId   源节点 id（必填）
     * @param relType 关系类型过滤（null = 不过滤）
     * @param dstType 目标类型过滤（null = 不过滤）
     * @param depth   遍历深度（缺省 1，≤2；&gt;2 拒绝）
     * @param status  状态过滤（null = 仅启用边）
     * @return 邻居列表；无命中/降级为空列表
     * @throws BizException srcType/srcId 缺失，或 depth 超出能力边界（1101，400）
     */
    public List<KgNeighborCO> neighbors(String srcType, Long srcId, String relType,
                                        String dstType, Integer depth, Integer status) {
        validateSrc(srcType, srcId);
        return kgClient.neighbors(srcType, srcId, relType, dstType, normalizeDepth(depth), status);
    }

    // ==================== 内部方法 ====================

    /** 类目存在性校验（悬空 → 1101，BDD §16.6.5 治理规则归属参数校验）。 */
    private void requireCategoryExists(Long categoryId, String role) {
        Category category = categoryRepository.findById(categoryId);
        if (category == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, role + "不存在: " + categoryId);
        }
    }

    /** 业务唯一键先查（主路径）；并发竞态由 uk_src_dst 唯一键兜底。 */
    private void ensurePairAvailable(Long srcCategoryId, Long dstCategoryId) {
        if (pairingRuleRepository.findBySrcAndDst(srcCategoryId, dstCategoryId) != null) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "搭配规则已存在: " + srcCategoryId + "->" + dstCategoryId);
        }
    }

    /** 按契约寻址键取规则，不存在抛 NO_RESOURCE_FOUND（AD63 契约错误口径 1103）。 */
    private PairingRule requireById(Long id) {
        PairingRule rule = pairingRuleRepository.findById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "搭配规则不存在: " + id);
        }
        return rule;
    }

    /** 邻居查询 src 必填守卫（search 侧 {@code KgEdgeManager} 同款）：缺失 → 1101。 */
    private static void validateSrc(String srcType, Long srcId) {
        if (srcType == null || srcType.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "srcType 不能为空");
        }
        if (srcId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "srcId 不能为空");
        }
    }

    /** depth 归一（null/非正 → 缺省 1）；&gt;2 超出图查询能力边界 → 1101（PRD §16.4 ≤2 跳）。 */
    private static int normalizeDepth(Integer depth) {
        if (depth == null || depth < MIN_NEIGHBOR_DEPTH) {
            return DEFAULT_NEIGHBOR_DEPTH;
        }
        if (depth > MAX_NEIGHBOR_DEPTH) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "depth 超出图查询能力边界(≤" + MAX_NEIGHBOR_DEPTH + "跳): " + depth);
        }
        return depth;
    }

    /** 重建①②：商品真相源逐批扫描——在售 upsert 直建边，下架/删除清边。 */
    private int rebuildProductEdges() {
        int written = 0;
        int pageNum = 1;
        while (true) {
            IPage<Product> batch = productRepository.pageAll(pageNum, REBUILD_PAGE_SIZE);
            if (batch.getRecords().isEmpty()) {
                break;
            }
            for (Product product : batch.getRecords()) {
                written += kgEdgeIngestManager.writeProductEdges(product);
            }
            if (pageNum >= batch.getPages()) {
                break;
            }
            pageNum++;
        }
        return written;
    }

    /**
     * 重建③：类目 PARENT_OF 边（category.parent_id 真相，Phase 1 仅由重建覆盖——
     * 类目树低频变更，不做逐次摄取）。按父类目分组逐 src 批量写入。
     */
    private int rebuildCategoryParentEdges() {
        Map<Long, List<Long>> childrenByParent = categoryRepository.findAllOrderBySort().stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId,
                        Collectors.mapping(Category::getId, Collectors.toList())));
        int written = 0;
        for (Map.Entry<Long, List<Long>> entry : childrenByParent.entrySet()) {
            List<KgEdgeItemCO> edges = entry.getValue().stream()
                    .map(childId -> new KgEdgeItemCO(KgSchema.REL_PARENT_OF, KgSchema.NODE_CATEGORY,
                            childId, 0, KgSchema.SOURCE_DIRECT, KgSchema.EDGE_ENABLED))
                    .toList();
            written += kgEdgeIngestManager.upsertSafely(
                    new KgEdgeUpsertCO(KgSchema.NODE_CATEGORY, entry.getKey(), edges));
        }
        return written;
    }

    /** 重建④：规则 PAIR_WITH 边——按源类目分组逐 src 写入（含停用规则，status 原样透传）。 */
    private int rebuildPairingRuleEdges() {
        Map<Long, List<PairingRule>> rulesBySrc = pairingRuleRepository.findAll().stream()
                .collect(Collectors.groupingBy(PairingRule::getSrcCategoryId));
        int written = 0;
        for (Map.Entry<Long, List<PairingRule>> entry : rulesBySrc.entrySet()) {
            written += kgEdgeIngestManager.writePairingEdges(entry.getKey(), entry.getValue());
        }
        return written;
    }

    /** 规则聚合 → 管理端视图（id 为 AD63 寻址口径，随 VO 透出）。 */
    private PairingRuleVO toVO(PairingRule rule) {
        PairingRuleVO vo = new PairingRuleVO();
        vo.setId(rule.getId());
        vo.setSrcCategoryId(rule.getSrcCategoryId());
        vo.setDstCategoryId(rule.getDstCategoryId());
        vo.setWeight(rule.getWeight());
        vo.setRemark(rule.getRemark());
        vo.setStatus(rule.getStatus());
        vo.setCreateTime(rule.getCreateTime());
        vo.setUpdateTime(rule.getUpdateTime());
        return vo;
    }
}
