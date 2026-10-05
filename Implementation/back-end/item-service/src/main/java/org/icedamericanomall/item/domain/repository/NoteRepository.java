package org.icedamericanomall.item.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.item.domain.model.content.Note;

import java.util.List;

/**
 * 种草笔记仓储（领域端口）—— 实现方：{@code infrastructure.persistence.content.NoteRepositoryImpl}。
 *
 * <p>分页返回 MyBatis-Plus {@code IPage} 信封：笔记流是既有 HTTP 契约（有界务实豁免，
 * 对齐 Review/Product 仓储先例）。
 */
public interface NoteRepository {

    /** 新增笔记（返回时回填技术主键到聚合）。 */
    void save(Note note);

    /** 按 ID 载入（可为 null；不过滤状态——已删/已发布判定由应用层按用例语义做）。 */
    Note findById(Long noteId);

    /** 按 ID 集合批量载入（不过滤状态——发布态过滤由应用层按用例语义做；P34 相似笔记组装）。 */
    List<Note> findAllByIds(List<Long> noteIds);

    /** 已发布笔记分页（status=1，发布时间倒序，P29 流）。 */
    IPage<Note> pagePublished(int page, int size);

    /** 点赞数原子 +1（单条 SQL {@code like_count = like_count + 1}，禁读-改-写）。 */
    int incrementLikeCount(Long noteId);

    /** 点赞数原子减 1 且不为负（单条 SQL {@code GREATEST(like_count - 1, 0)}）。 */
    int decrementLikeCount(Long noteId);

    /** 状态覆盖更新（软删 status=2 由聚合 {@code Note#softDelete()} 决定后落库）。 */
    void updateStatus(Long noteId, Integer status);
}
