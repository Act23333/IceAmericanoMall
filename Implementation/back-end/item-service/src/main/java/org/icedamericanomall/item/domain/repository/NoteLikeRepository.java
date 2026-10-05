package org.icedamericanomall.item.domain.repository;

/**
 * 笔记点赞仓储（领域端口）—— 实现方：{@code infrastructure.persistence.content.NoteLikeRepositoryImpl}。
 *
 * <p>{@code note_like} 是 Note 聚合内部关联表（{@code uk_note_user} 点赞幂等，
 * ADR-048），只允许经本端口访问。
 */
public interface NoteLikeRepository {

    /**
     * 插入点赞记录。
     *
     * <p><b>uk 撞上抛</b>：同一用户重复点赞同一笔记触发唯一键冲突
     * （Spring {@code DuplicateKeyException}）——幂等语义由应用层回落处理，本端口不吞。
     *
     * @return 影响行数（成功为 1）
     */
    int insertLike(Long noteId, Long userId);

    /**
     * 删除点赞记录（P32 取消点赞）。
     *
     * @return 影响行数；0 = 本未点赞（空操作）
     */
    int deleteByNoteAndUser(Long noteId, Long userId);

    /** 当前用户是否已点赞该笔记（详情 {@code liked} 回填口径）。 */
    boolean existsByNoteAndUser(Long noteId, Long userId);
}
