package org.icedamericanomall.item.infrastructure.persistence.content;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 种草笔记 Mapper（{@code note}）—— 基础 CRUD 走 MyBatis-Plus BaseMapper；
 * 点赞计数为单条原子 SQL（禁读-改-写，MySQL 单行 UPDATE 即精确，ADR-048）。
 */
public interface NoteMapper extends BaseMapper<NotePO> {

    /**
     * 点赞计数原子 +1 —— 插入 {@code note_like} 成功后调用（P31）。
     *
     * <p>单条 SQL 原子自增，无读-改-写竞态；0 行 = 笔记不存在（调用方保证先校验存在）。
     */
    @Update("UPDATE note SET like_count = like_count + 1 WHERE id = #{noteId}")
    int incrementLikeCount(@Param("noteId") Long noteId);

    /**
     * 点赞计数原子减 1 且不为负 —— 删除 {@code note_like} 行成功后调用（P32）。
     *
     * <p>{@code GREATEST} 兜底不为负（历史口径防御）；单条 SQL 原子。
     */
    @Update("UPDATE note SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{noteId}")
    int decrementLikeCount(@Param("noteId") Long noteId);
}
