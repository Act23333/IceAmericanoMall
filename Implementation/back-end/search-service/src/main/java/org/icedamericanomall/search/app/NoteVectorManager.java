package org.icedamericanomall.search.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.dto.NoteVectorHitCO;
import org.icedamericanomall.dto.NoteVectorUpsertCO;
import org.icedamericanomall.search.domain.gateway.EmbeddingGateway;
import org.icedamericanomall.search.domain.gateway.NoteVectorIndexGateway;
import org.icedamericanomall.search.domain.model.search.NoteVectorDoc;
import org.icedamericanomall.search.domain.model.search.NoteVectorHit;
import org.icedamericanomall.search.domain.service.NoteVectorTextBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 笔记向量编排（应用层）—— 摄取 / 相似检索（契约 I50/I51，V5.13，ADR-046 管道第二次复用）。
 *
 * <p><b>摄取链路（I50）</b>：笔记原文 → 拼接向量化文本（{@link NoteVectorTextBuilder}，
 * {@code title + content} 单一事实源）→ {@link EmbeddingGateway} 单向量 →
 * {@link NoteVectorIndexGateway#upsertVectorDoc(NoteVectorDoc)} 幂等覆盖写（noteId 幂等键）；
 * {@code deleted=true}（软删/取消发布语义）→ 直接删除该向量文档返回 1（删除不依赖 Embedding 配置）。
 *
 * <p><b>相似检索链路（I51）</b>：取自身向量 → 以自身向量 kNN（Top-K = size + 1）→ 过滤自身
 * → 只出 {@code noteId + score}（笔记卡组装与发布态过滤归 item-service：MySQL 是展示真相源）。
 *
 * <p><b>渐进启用 / 全链路 fail-open（推荐是纯旁路）</b>：与商品向量（{@link SpuVectorManager}）同口径——
 * Embedding 未配置 → 摄取返回 0 并记 WARN，<b>不报错</b>；Embedding/写入失败 → 抛业务异常（fail-loud），
 * 由调用方（item-service）经 Feign 降级为哨兵；自身无向量（冷启动存量笔记）→ 相似检索返回空列表。
 *
 * <p>仅 {@code search.elasticsearch.enabled=true} 时装配（依赖 ES 网关实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class NoteVectorManager {

    /** 相似检索缺省条数（契约 I51：size 缺省 5）。 */
    private static final int DEFAULT_SIMILAR_SIZE = 5;

    /** 相似检索上限（契约 I51：size ≤ 20）。 */
    private static final int MAX_SIMILAR_SIZE = 20;

    /** 命中无得分（理论不发生，仅防御）时的兜底相似度。 */
    private static final double NO_SCORE = 0.0D;

    private final EmbeddingGateway embeddingGateway;
    private final NoteVectorIndexGateway noteVectorIndexGateway;

    /**
     * 笔记向量摄取：{@code deleted=true} 删向量文档（返回 1），否则拼接文本 → 单向量 → 幂等覆盖写。
     *
     * @param co 笔记原文（noteId 幂等键；deleted=true 表示软删/取消发布，其余字段忽略）
     * @return 1=已写入/已删除；0=Embedding 未启用（未启用不报错，调用方零感知）
     * @throws org.icedamericanomall.common.exception.BizException 向量化或写入失败（调用方降级为哨兵）
     */
    public int upsert(NoteVectorUpsertCO co) {
        if (co.isDeleted()) {
            noteVectorIndexGateway.deleteById(co.getNoteId());
            log.info("笔记向量已删除（软删/取消发布语义）: noteId={}", co.getNoteId());
            return 1;
        }
        if (!embeddingGateway.isConfigured()) {
            log.warn("笔记向量摄取未启用（search.embedding 未配置），跳过: noteId={}", co.getNoteId());
            return 0;
        }
        float[] vector = embeddingGateway.embed(NoteVectorTextBuilder.build(co.getTitle(), co.getContent()));
        noteVectorIndexGateway.upsertVectorDoc(toVectorDoc(co, vector));
        log.info("笔记向量已写入: noteId={}", co.getNoteId());
        return 1;
    }

    /**
     * 相似笔记检索：自身向量 kNN，排除自身，按相似度降序（排序由 ES kNN 保证）。
     *
     * @param noteId 笔记 id（取自身向量）
     * @param size   返回条数（≤0 归一 5，&gt;20 截 20）
     * @return 命中列表（noteId + score）；自身无向量/无命中时为空列表
     */
    public List<NoteVectorHitCO> similar(Long noteId, Integer size) {
        int topK = normalizeSize(size);
        Optional<double[]> selfVector = noteVectorIndexGateway.findVectorById(noteId);
        if (selfVector.isEmpty() || selfVector.get().length == 0) {
            return List.of();
        }
        // 多取 1 条兜底「命中含自身被过滤」，补回不足的条数
        List<NoteVectorHit> hits = noteVectorIndexGateway.similar(selfVector.get(), topK + 1);
        return hits.stream()
                .filter(hit -> hit.noteId() != null && !hit.noteId().equals(noteId))
                .limit(topK)
                .map(NoteVectorManager::toHitCO)
                .toList();
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIMILAR_SIZE;
        }
        return Math.min(size, MAX_SIMILAR_SIZE);
    }

    /** 契约 CO → 领域向量文档（float → double 对齐 {@code NoteVectorDocument.embedding}）。 */
    private static NoteVectorDoc toVectorDoc(NoteVectorUpsertCO co, float[] vector) {
        double[] embedding = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            embedding[i] = vector[i];
        }
        return new NoteVectorDoc(co.getNoteId(), co.getTitle(), co.getContent(), embedding);
    }

    private static NoteVectorHitCO toHitCO(NoteVectorHit hit) {
        NoteVectorHitCO co = new NoteVectorHitCO();
        co.setNoteId(hit.noteId());
        co.setScore(hit.score() == null ? NO_SCORE : hit.score());
        return co;
    }
}
