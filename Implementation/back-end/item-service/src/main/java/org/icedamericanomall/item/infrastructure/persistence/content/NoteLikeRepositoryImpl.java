package org.icedamericanomall.item.infrastructure.persistence.content;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.domain.repository.NoteLikeRepository;
import org.springframework.stereotype.Repository;

/**
 * 笔记点赞仓储实现 —— MyBatis-Plus 访问 {@code note_like} 表。
 *
 * <p>insert 撞 {@code uk_note_user} 时 MyBatis-Spring 将 1062 翻译为
 * {@code DuplicateKeyException} <b>原样上抛</b>（幂等回落由应用层决定，本类不吞）。
 */
@Repository
@RequiredArgsConstructor
public class NoteLikeRepositoryImpl implements NoteLikeRepository {

    private final NoteLikeMapper noteLikeMapper;

    @Override
    public int insertLike(Long noteId, Long userId) {
        return noteLikeMapper.insertLike(noteId, userId);
    }

    @Override
    public int deleteByNoteAndUser(Long noteId, Long userId) {
        return noteLikeMapper.deleteLike(noteId, userId);
    }

    @Override
    public boolean existsByNoteAndUser(Long noteId, Long userId) {
        return noteLikeMapper.countByNoteAndUser(noteId, userId) > 0;
    }
}
