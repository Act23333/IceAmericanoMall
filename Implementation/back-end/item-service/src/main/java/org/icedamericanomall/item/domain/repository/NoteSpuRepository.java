package org.icedamericanomall.item.domain.repository;

import java.util.List;

/**
 * 笔记关联商品仓储（领域端口）—— 实现方：{@code infrastructure.persistence.content.NoteSpuRepositoryImpl}。
 *
 * <p>{@code note_spu} 是 Note 聚合内部关联表（≤5 条，{@code uk_note_spu}，ADR-048），
 * 只允许经本端口访问；商品存在性/在售校验属跨聚合协作，由应用层在写入前完成。
 */
public interface NoteSpuRepository {

    /** 批量写入关联商品（按传入顺序写 sort=0..n-1；入参须已去重，≤{@code Note#MAX_SPUS}）。 */
    void saveAll(Long noteId, List<Long> spuIds);

    /**
     * 查询笔记关联商品 ID（按 sort 序 = 发布时的关联顺序）。
     *
     * @return 有序商品 ID 列表（无关联时为空列表）
     */
    List<Long> findSpuIdsByNoteId(Long noteId);
}
