package org.icedamericanomall.ai.app.knowledge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.app.dto.KnowledgeDTO;
import org.icedamericanomall.ai.domain.model.knowledge.KnowledgeEntry;
import org.icedamericanomall.ai.domain.model.knowledge.KnowledgeVectorDoc;
import org.icedamericanomall.ai.domain.port.KnowledgeVectorPort;
import org.icedamericanomall.ai.domain.repository.MerchantKnowledgeRepository;
import org.icedamericanomall.ai.infrastructure.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商家知识库（应用层）—— 发布 / 更新 / 软删除 / 列表（按商家隔离），并同步向量索引。
 *
 * <p>持久化经 {@link MerchantKnowledgeRepository} 端口（状态过滤与 {@code id + sellerId}
 * 双键限定收敛在仓储实现）；出参为应用层 DTO（{@link KnowledgeDTO}），由适配层
 * {@code KnowledgeConvertor} 转 VO——应用层不返回 VO，也不把 {@code embeddingId}
 * 这类内部句柄带到出参。
 *
 * <h2>向量索引同步（V5.4，渐进启用 + 全链路降级）</h2>
 * <ul>
 *   <li>仅当 {@code ai.rag.enabled=true} 时经 {@link KnowledgeVectorPort} 触达 search-service；
 *       <b>关闭时零触达</b>（行为与「RAG 未接入」完全一致）。</li>
 *   <li>分块与向量化都由 search-service 完成；本层只提交原文。</li>
 *   <li><b>失败绝不断主流程</b>：端口本身 fail-open（返回哨兵值不抛异常），此处再包一层
 *       try/catch + WARN（双保险）。索引可在后续任意一次保存时幂等重建。</li>
 *   <li>本方法链<b>不开启事务</b>：事务内不得跨服务调用（跨服务一致性走最终一致方案），
 *       且向量索引是旁路产物，无需与 MySQL 写入原子。</li>
 * </ul>
 *
 * <h3>{@code embedding_id} 列口径</h3>
 * 摄取成功后写入 {@code es:{knowledgeId}}（前缀常量 {@link #EMBEDDING_ID_PREFIX}），表示
 * 「该条目已在向量索引中、以 knowledgeId 为幂等键」。之所以不用 chunk 数：chunk 数会随
 * 切片策略调整而变化，而 {@code es:{knowledgeId}} 是稳定、可反查的索引归属标识；
 * 摄取失败或未启用时该列保持为 <b>null</b>（不写脏值）。该列不下传到 DTO/VO。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseManager {

    /** embedding_id 前缀（表示该条目已同步至向量索引，后接 knowledgeId）。 */
    private static final String EMBEDDING_ID_PREFIX = "es:";

    private final MerchantKnowledgeRepository knowledgeRepository;
    private final KnowledgeVectorPort knowledgeVectorPort;
    private final AiProperties aiProperties;

    /** 商家条目列表（可按分类筛选；软删条目由仓储过滤）。 */
    public List<KnowledgeDTO> listBySeller(Long sellerId, String category) {
        return knowledgeRepository.findBySeller(sellerId, category)
                .stream()
                .map(KnowledgeBaseManager::toDTO)
                .toList();
    }

    /** 新建条目（归属由登录上下文指定；空标题仍按原语义放行，由前端/后续迭代约束）。 */
    public KnowledgeDTO create(Long sellerId, String title, String content, String category) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setSellerId(sellerId);
        entry.setTitle(title);
        entry.setContent(content);
        entry.setCategory(category);
        entry.setStatus(KnowledgeEntry.STATUS_ACTIVE);
        KnowledgeEntry saved = knowledgeRepository.save(entry);
        syncVectorIndex(saved);
        return toDTO(saved);
    }

    /** 按 {id, sellerId} 查询条目（不存在返回 null）。 */
    public KnowledgeDTO findById(Long id, Long sellerId) {
        KnowledgeEntry entry = knowledgeRepository.findByIdAndSeller(id, sellerId);
        return entry == null ? null : toDTO(entry);
    }

    /**
     * null-safe 更新条目（仅限本人条目）。
     *
     * <p>更新后按幂等口径重写向量索引（search-service 侧先删后写）。
     *
     * @return false = 条目不存在 / 非本人（控制器据此返回 404）
     */
    public boolean update(Long id, Long sellerId, String title, String content, String category) {
        KnowledgeEntry entry = knowledgeRepository.findByIdAndSeller(id, sellerId);
        if (entry == null) {
            return false;
        }
        entry.mergePatch(title, content, category);
        knowledgeRepository.update(entry);
        syncVectorIndex(entry);
        return true;
    }

    /** 软删除条目（仅限本人条目），并清理向量索引。 */
    public void softDelete(Long id, Long sellerId) {
        KnowledgeEntry entry = knowledgeRepository.findByIdAndSeller(id, sellerId);
        if (entry != null) {
            entry.setStatus(KnowledgeEntry.STATUS_DELETED);
            knowledgeRepository.update(entry);
            removeVectorIndex(id);
        }
    }

    /**
     * 同步向量索引：成功则以 {@code es:{knowledgeId}} 回填 embedding_id。
     *
     * @param entry 已落库的知识条目（须有 id）
     */
    private void syncVectorIndex(KnowledgeEntry entry) {
        if (!aiProperties.getRag().isEnabled()) {
            return;
        }
        try {
            int chunks = knowledgeVectorPort.upsertKnowledgeVector(new KnowledgeVectorDoc(
                    entry.getId(), entry.getSellerId(), entry.getTitle(), entry.getContent()));
            if (chunks > 0) {
                entry.setEmbeddingId(EMBEDDING_ID_PREFIX + entry.getId());
                knowledgeRepository.update(entry);
                log.info("知识库向量索引已同步: knowledgeId={}, chunks={}", entry.getId(), chunks);
            } else {
                log.warn("知识库向量索引未建立（向量链路未启用/失败）: knowledgeId={}", entry.getId());
            }
        } catch (Exception e) {
            log.warn("知识库向量索引同步失败（降级，不影响知识库主流程）: knowledgeId={}, cause={}",
                    entry.getId(), e.getMessage());
        }
    }

    /** 清理向量索引（失败降级为 WARN）。 */
    private void removeVectorIndex(Long knowledgeId) {
        if (!aiProperties.getRag().isEnabled()) {
            return;
        }
        try {
            knowledgeVectorPort.deleteKnowledgeVector(knowledgeId);
        } catch (Exception e) {
            log.warn("知识库向量索引清理失败（降级，不影响知识库主流程）: knowledgeId={}, cause={}",
                    knowledgeId, e.getMessage());
        }
    }

    /** 领域条目 → 应用层 DTO（embeddingId 不下传）。 */
    private static KnowledgeDTO toDTO(KnowledgeEntry entry) {
        KnowledgeDTO dto = new KnowledgeDTO();
        dto.setId(entry.getId());
        dto.setSellerId(entry.getSellerId());
        dto.setTitle(entry.getTitle());
        dto.setContent(entry.getContent());
        dto.setCategory(entry.getCategory());
        dto.setStatus(entry.getStatus());
        dto.setCreateTime(entry.getCreateTime());
        dto.setUpdateTime(entry.getUpdateTime());
        return dto;
    }
}
