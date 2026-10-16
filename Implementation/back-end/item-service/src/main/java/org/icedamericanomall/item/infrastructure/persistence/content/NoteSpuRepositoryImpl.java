package org.icedamericanomall.item.infrastructure.persistence.content;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.domain.repository.NoteSpuRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 笔记关联商品仓储实现 —— MyBatis-Plus 访问 {@code note_spu} 表。
 *
 * <p>≤{@code Note#MAX_SPUS} 条小批量写：按传入顺序逐条写 sort=0..n-1，
 * 循环规模有界（≤5），发布事务内执行。
 */
@Repository
@RequiredArgsConstructor
public class NoteSpuRepositoryImpl implements NoteSpuRepository {

    private final NoteSpuMapper noteSpuMapper;

    @Override
    public void saveAll(Long noteId, List<Long> spuIds) {
        for (int sort = 0; sort < spuIds.size(); sort++) {
            noteSpuMapper.insertOne(noteId, spuIds.get(sort), sort);
        }
    }

    @Override
    public List<Long> findSpuIdsByNoteId(Long noteId) {
        return noteSpuMapper.selectSpuIdsByNoteId(noteId);
    }
}
