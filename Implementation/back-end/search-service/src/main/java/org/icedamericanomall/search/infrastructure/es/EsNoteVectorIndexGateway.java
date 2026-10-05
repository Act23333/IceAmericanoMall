package org.icedamericanomall.search.infrastructure.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.search.domain.gateway.NoteVectorIndexGateway;
import org.icedamericanomall.search.domain.model.search.NoteVectorDoc;
import org.icedamericanomall.search.domain.model.search.NoteVectorHit;
import org.icedamericanomall.search.infrastructure.config.EmbeddingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code notes} 索引向量网关 —— 笔记向量 I50/I51 的 ES 读写实现
 * （ADR-046 管道第二次复用，结构镜像 {@link EsProductSearchGateway} 的向量部分）。
 *
 * <p>幂等口径：整文档覆盖写（_id = noteId，重复摄取即覆盖）；软删/取消发布走
 * {@link #deleteById(Long)}。仅 {@code search.elasticsearch.enabled=true} 时装配。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class EsNoteVectorIndexGateway implements NoteVectorIndexGateway {

    /** 并发建索引时被其它实例抢先的错误特征串（无害化，与商品/知识库索引同口径）。 */
    private static final String ALREADY_EXISTS_MARKER = "resource_already_exists_exception";

    private final ElasticsearchOperations esOps;
    private final ElasticsearchClient esClient;
    private final EmbeddingProperties embeddingProperties;

    @Override
    public void upsertVectorDoc(NoteVectorDoc doc) {
        ensureIndex();
        esOps.save(toDocument(doc));
        log.info("笔记向量已写入: noteId={}", doc.noteId());
    }

    @Override
    public Optional<double[]> findVectorById(Long noteId) {
        try {
            NoteVectorDocument document = esOps.get(String.valueOf(noteId), NoteVectorDocument.class);
            return Optional.ofNullable(document).map(NoteVectorDocument::getEmbedding);
        } catch (Exception e) {
            // 索引不存在/读取失败等同「无向量」（fail-open），仅影响相似检索的空结果
            log.warn("笔记向量读取失败（按无向量处理）: noteId={}, 原因: {}", noteId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void deleteById(Long noteId) {
        ensureIndex();
        esOps.delete(String.valueOf(noteId), NoteVectorDocument.class);
        log.info("笔记向量已删除: noteId={}", noteId);
    }

    @Override
    public List<NoteVectorHit> similar(double[] embedding, int size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(buildKnnQuery(embedding, size))
                .withPageable(PageRequest.of(0, size))
                .build();

        SearchHits<NoteVectorDocument> hits = esOps.search(query, NoteVectorDocument.class);
        return hits.getSearchHits().stream()
                .map(hit -> new NoteVectorHit(hit.getContent().getNoteId(), (double) hit.getScore()))
                .toList();
    }

    /** kNN 查询（候选 = Top-K × 2，与商品向量检索同口径）。 */
    private static Query buildKnnQuery(double[] embedding, int size) {
        return Query.of(q -> q.knn(KnnQuery.of(k -> k
                .field(NoteVectorMapping.VECTOR_FIELD)
                .queryVector(toFloatList(embedding))
                .k(size)
                .numCandidates(size * 2))));
    }

    /** 确保 notes 索引存在（不存在按 kNN mapping 创建，并发被抢先静默通过；已有索引不变更）。 */
    private void ensureIndex() {
        try {
            if (esClient.indices().exists(e -> e.index(NoteVectorDocument.INDEX_NAME)).value()) {
                return;
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "笔记向量索引探测失败: " + e.getMessage());
        }
        createIndexWithMapping();
    }

    /** 按 {@link NoteVectorMapping} 创建索引（kNN 开关 + dense_vector，dims 同源 Embedding 配置）。 */
    private void createIndexWithMapping() {
        String mapping = NoteVectorMapping.buildJson(embeddingProperties.getDims());
        try {
            esClient.indices().create(c -> c.index(NoteVectorDocument.INDEX_NAME)
                    .withJson(new StringReader(mapping)));
            log.info("笔记向量索引已创建: index={}, dims={}",
                    NoteVectorDocument.INDEX_NAME, embeddingProperties.getDims());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains(ALREADY_EXISTS_MARKER)) {
                log.debug("笔记向量索引已由其它实例创建: index={}", NoteVectorDocument.INDEX_NAME);
                return;
            }
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "笔记向量索引创建失败: " + e.getMessage());
        }
    }

    /** 领域向量文档 → ES 文档（_id = noteId，幂等覆盖；原文一并以入参为准）。 */
    private static NoteVectorDocument toDocument(NoteVectorDoc doc) {
        NoteVectorDocument document = new NoteVectorDocument();
        document.setNoteId(doc.noteId());
        document.setTitle(doc.title());
        document.setContent(doc.content());
        document.setEmbedding(doc.embedding());
        return document;
    }

    private static List<Float> toFloatList(double[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (double value : array) {
            list.add((float) value);
        }
        return list;
    }
}
