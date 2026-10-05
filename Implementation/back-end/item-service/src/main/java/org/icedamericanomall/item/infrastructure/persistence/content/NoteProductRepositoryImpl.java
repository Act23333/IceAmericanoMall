package org.icedamericanomall.item.infrastructure.persistence.content;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.domain.repository.NoteProductRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 笔记关联商品仓储实现 —— MyBatis-Plus 访问 {@code note_product} 表。
 *
 * <p>≤{@code Note#MAX_PRODUCTS} 条小批量写：按传入顺序逐条写 sort=0..n-1，
 * 循环规模有界（≤5），发布事务内执行。
 */
@Repository
@RequiredArgsConstructor
public class NoteProductRepositoryImpl implements NoteProductRepository {

    private final NoteProductMapper noteProductMapper;

    @Override
    public void saveAll(Long noteId, List<Long> productIds) {
        for (int sort = 0; sort < productIds.size(); sort++) {
            noteProductMapper.insertOne(noteId, productIds.get(sort), sort);
        }
    }

    @Override
    public List<Long> findProductIdsByNoteId(Long noteId) {
        return noteProductMapper.selectProductIdsByNoteId(noteId);
    }
}
