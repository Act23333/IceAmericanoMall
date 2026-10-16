package org.icedamericanomall.search.domain.gateway;

import org.icedamericanomall.search.domain.model.search.NoteVectorDoc;
import org.icedamericanomall.search.domain.model.search.NoteVectorHit;

import java.util.List;
import java.util.Optional;

/**
 * 笔记向量索引网关（领域端口）—— {@code notes} 索引的写/删/取向量/kNN 检索抽象
 * （契约 I50/I51，V5.13；与商品向量 {@link SpuSearchGateway} 同构，ADR-046 管道第二次复用）。
 *
 * <p>实现方：{@code infrastructure.es.EsNoteVectorIndexGateway}（{@code search.elasticsearch.enabled=true}）
 * 与 {@code infrastructure.persistence.DbNoteVectorIndexGateway}（DB 链空实现，向量是 ES 能力），
 * 两者条件互补、同时最多装配一个。
 */
public interface NoteVectorIndexGateway {

    /**
     * 笔记向量文档幂等覆盖写（契约 I50）—— 按 noteId 幂等，_id 同源，整文档以入参为准。
     * DB 实现为空操作。
     *
     * @param doc 笔记向量文档（原文 + 向量）
     */
    void upsertVectorDoc(NoteVectorDoc doc);

    /**
     * 取笔记自身向量（供 I51 以自身向量做 kNN 检索）。
     * DB 实现恒返回 empty；ES 实现在笔记未摄取/读取失败时也返回 empty（fail-open）。
     *
     * @param noteId 笔记 id（幂等键，与 {@link #upsertVectorDoc(NoteVectorDoc)} 同源）
     * @return 笔记向量；不存在时 empty
     */
    Optional<double[]> findVectorById(Long noteId);

    /**
     * 删除笔记向量文档（契约 I50 的 {@code deleted=true} 软删/取消发布语义）。
     * DB 实现为空操作。
     *
     * @param noteId 笔记 id
     */
    void deleteById(Long noteId);

    /**
     * 笔记向量 kNN 相似检索（契约 I51 的检索底座，Top-K 按相似度降序）。
     * 排除自身由应用层（{@code NoteVectorManager}）负责（kNN 多取 1 条后过滤）。
     * DB 实现恒返回空列表；ES 实现检索失败上抛（fail-loud，与商品向量检索同口径，
     * 由调用方 item-service 经 Feign 降级为空列表）。
     *
     * @param embedding 查询向量（调用方的自身向量）
     * @param size      Top-K 条数
     * @return 命中列表（noteId + score）
     */
    List<NoteVectorHit> similar(double[] embedding, int size);
}
