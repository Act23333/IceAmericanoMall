package org.icedamericanomall.search.adapter.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.KnowledgeVectorClient;
import org.icedamericanomall.dto.NoteVectorHitCO;
import org.icedamericanomall.dto.NoteVectorUpsertCO;
import org.icedamericanomall.dto.SpuVectorHitCO;
import org.icedamericanomall.dto.SpuVectorUpsertCO;
import org.icedamericanomall.search.adapter.assembler.SpuSearchConverter;
import org.icedamericanomall.search.adapter.request.KnowledgeVectorUpsertReq;
import org.icedamericanomall.search.adapter.request.NoteVectorUpsertReq;
import org.icedamericanomall.search.adapter.request.SpuVectorUpsertReq;
import org.icedamericanomall.search.adapter.request.VectorSearchReq;
import org.icedamericanomall.search.adapter.response.KnowledgeChunkHitVO;
import org.icedamericanomall.search.app.KnowledgeVectorManager;
import org.icedamericanomall.search.app.NoteVectorManager;
import org.icedamericanomall.search.app.SpuVectorManager;
import org.icedamericanomall.search.app.SearchManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 内部搜索管理接口 — 索引重建 / 向量检索 / 知识库向量摄取 / 商品与笔记向量摄取与相似检索。
 *
 * <p>内部接口（{@code /internal/**}）按约定不包装 {@code Result}，异常原样透出。
 * Controller 仅做参数解析 + 委托 app 层（{@link SearchManager} / {@link KnowledgeVectorManager}
 * / {@link SpuVectorManager} / {@link NoteVectorManager}）。
 *
 * <p><b>向量索引归属</b>：{@code knowledge_vectors} 索引（含分块与 Embedding 调用）归本服务所有；
 * ai-service 通过 {@link KnowledgeVectorClient} 提交<b>原文</b>并取回 chunk 文本，不感知维度与切片策略。
 * {@code spus} 索引同理归本服务（I47/I48 商品向量推荐，ADR-046）；
 * {@code notes} 索引亦然（I50/I51 笔记向量推荐，V5.13，ADR-046 管道第二次复用）。
 *
 * <p><b>渐进启用 / 降级</b>：ES 关闭（{@code search.elasticsearch.enabled=false}）时
 * {@link KnowledgeVectorManager} / {@link SpuVectorManager} / {@link NoteVectorManager} 不存在，
 * 知识库与商品/笔记向量端点按「未启用」处理（摄取返回 0、删除返回 false、检索返回空），<b>不抛异常</b>，
 * 从而不阻断 ai-service / item-service 的主流程（推荐为纯旁路，fail-open）。
 */
@Slf4j
@RestController
@RequestMapping("/internal/search")
@RequiredArgsConstructor
public class InternalSearchController {

    private final SearchManager searchManager;
    private final SpuSearchConverter converter;

    /** ES 关闭时无此 Bean，故用 ObjectProvider 软依赖（不阻断启动，按未启用处理）。 */
    private final ObjectProvider<KnowledgeVectorManager> knowledgeVectorManager;

    /** ES 关闭时无此 Bean，故用 ObjectProvider 软依赖（不阻断启动，按未启用处理）。 */
    private final ObjectProvider<SpuVectorManager> spuVectorManager;

    /** ES 关闭时无此 Bean，故用 ObjectProvider 软依赖（不阻断启动，按未启用处理）。 */
    private final ObjectProvider<NoteVectorManager> noteVectorManager;

    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        return searchManager.reindex();
    }

    /**
     * 向量 kNN 检索：{@code index=spus}（缺省，历史行为）走商品索引；
     * {@code index=knowledge_vectors} 走知识库 chunk 索引（支持 sellerId 过滤）。
     */
    @PostMapping("/vector")
    public List<?> vectorSearch(@RequestBody VectorSearchReq req) {
        if (KnowledgeVectorClient.INDEX_KNOWLEDGE_VECTORS.equals(req.normalizedIndex())) {
            return searchKnowledgeChunks(req);
        }
        List<Double> embedding = req.embedding();
        if (embedding == null || embedding.isEmpty()) {
            return List.of();
        }
        double[] vector = embedding.stream().mapToDouble(Double::doubleValue).toArray();
        return converter.hitsToVOs(searchManager.vectorSearch(vector, req.size()));
    }

    /**
     * 知识库向量摄取 —— 服务端分块 + 向量化 + 幂等重写。
     *
     * @return 写入 chunk 数；Embedding/ES 未启用返回 0（不报错）
     */
    @PostMapping("/vector/knowledge/upsert")
    public int upsertKnowledgeVector(@RequestBody @Valid KnowledgeVectorUpsertReq req) {
        KnowledgeVectorManager manager = knowledgeVectorManager.getIfAvailable();
        if (manager == null) {
            log.warn("知识库向量摄取未启用（ES 关闭），跳过: knowledgeId={}", req.knowledgeId());
            return 0;
        }
        return manager.upsert(req.knowledgeId(), req.sellerId(), req.title(), req.content());
    }

    /**
     * 按 knowledgeId 删除知识库向量 chunk。
     *
     * @return 是否处理；ES 未启用返回 false
     */
    @PostMapping("/vector/knowledge/delete")
    public boolean deleteKnowledgeVector(@RequestParam(name = "knowledge_id") Long knowledgeId) {
        KnowledgeVectorManager manager = knowledgeVectorManager.getIfAvailable();
        if (manager == null) {
            log.warn("知识库向量删除未启用（ES 关闭），跳过: knowledgeId={}", knowledgeId);
            return false;
        }
        manager.delete(knowledgeId);
        return true;
    }

    /**
     * 商品向量摄取（契约 I47）—— 单向量幂等覆盖写（spuId 幂等键，ADR-046）。
     *
     * @return 1=已写入向量；0=ES/Embedding 未启用（静默跳过，不报错）
     */
    @PostMapping("/vector/product/upsert")
    public int upsertSpuVector(@RequestBody @Valid SpuVectorUpsertReq req) {
        SpuVectorManager manager = spuVectorManager.getIfAvailable();
        if (manager == null) {
            log.warn("商品向量摄取未启用（ES 关闭），跳过: spuId={}", req.spuId());
            return 0;
        }
        return manager.upsert(toCO(req));
    }

    /**
     * 相似商品检索（契约 I48）—— 自身向量 kNN，排除自身，只出 {@code spuId + score}
     * （商品卡组装归 item-service，MySQL 是展示真相源）。
     *
     * <p><b>入参口径（ADR-061 §决策 2）</b>：{@code spu_id} 为 SPU <b>业务键</b>（32-hex），
     * 与 I47 摄取幂等键、I48 命中 {@code spuId} 同源 —— 调用方（item-service）无需解析技术键。
     *
     * @return 命中列表；ES 关闭/自身无向量 → 空列表
     */
    @GetMapping("/vector/product/similar")
    public List<SpuVectorHitCO> similarSpuVector(
            @RequestParam(name = "spu_id") String spuId,
            @RequestParam(value = "size", defaultValue = "5") Integer size) {
        SpuVectorManager manager = spuVectorManager.getIfAvailable();
        if (manager == null) {
            log.warn("商品向量检索未启用（ES 关闭），按无命中处理: spuId={}", spuId);
            return List.of();
        }
        return manager.similar(spuId, size);
    }

    private List<KnowledgeChunkHitVO> searchKnowledgeChunks(VectorSearchReq req) {
        KnowledgeVectorManager manager = knowledgeVectorManager.getIfAvailable();
        if (manager == null) {
            log.warn("知识库向量检索未启用（ES 关闭），按无命中处理");
            return List.of();
        }
        return manager.searchKnowledge(req.embedding(), req.size(), req.sellerId()).stream()
                .map(KnowledgeChunkHitVO::from)
                .toList();
    }

    /**
     * 笔记向量摄取（契约 I50）—— {@code deleted=true} 语义删向量文档，否则单向量幂等覆盖写
     * （noteId 幂等键，V5.13，ADR-046 管道第二次复用）。
     *
     * @return 1=已写入/已删除；0=ES/Embedding 未启用（静默跳过，不报错）
     */
    @PostMapping("/vector/note/upsert")
    public int upsertNoteVector(@RequestBody @Valid NoteVectorUpsertReq req) {
        NoteVectorManager manager = noteVectorManager.getIfAvailable();
        if (manager == null) {
            log.warn("笔记向量摄取未启用（ES 关闭），跳过: noteId={}", req.noteId());
            return 0;
        }
        return manager.upsert(toNoteCO(req));
    }

    /**
     * 相似笔记检索（契约 I51）—— 自身向量 kNN，排除自身，只出 {@code noteId + score}
     * （笔记卡组装与发布态过滤归 item-service，MySQL 是展示真相源）。
     *
     * @return 命中列表；ES 关闭/自身无向量 → 空列表
     */
    @GetMapping("/vector/note/similar")
    public List<NoteVectorHitCO> similarNoteVector(
            @RequestParam(name = "note_id") Long noteId,
            @RequestParam(value = "size", defaultValue = "5") Integer size) {
        NoteVectorManager manager = noteVectorManager.getIfAvailable();
        if (manager == null) {
            log.warn("笔记向量检索未启用（ES 关闭），按无命中处理: noteId={}", noteId);
            return List.of();
        }
        return manager.similar(noteId, size);
    }

    /** 适配层入参 → 契约 CO（字段一一对应，纯搬运）。 */
    private static NoteVectorUpsertCO toNoteCO(NoteVectorUpsertReq req) {
        NoteVectorUpsertCO co = new NoteVectorUpsertCO();
        co.setNoteId(req.noteId());
        co.setTitle(req.title());
        co.setContent(req.content());
        co.setDeleted(req.deleted());
        return co;
    }

    /** 适配层入参 → 契约 CO（字段一一对应，纯搬运）。 */
    private static SpuVectorUpsertCO toCO(SpuVectorUpsertReq req) {
        SpuVectorUpsertCO co = new SpuVectorUpsertCO();
        co.setSpuId(req.spuId());
        co.setName(req.name());
        co.setDescription(req.description());
        co.setBrand(req.brand());
        co.setCategoryId(req.categoryId());
        co.setMainImage(req.mainImage());
        co.setPrice(req.price());
        co.setSoldCount(req.soldCount());
        return co;
    }
}
