package org.icedamericanomall.item.infrastructure.persistence.content;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 笔记点赞 Mapper（{@code note_like}）—— {@code uk_note_user} 幂等口径：
 * 重复点赞由唯一键拒绝（异常上抛，应用层回落为空操作），删除/查询按业务键
 * （note_id + user_id），禁止按技术主键操作。
 */
public interface NoteLikeMapper extends BaseMapper<NoteLikePO> {

    /**
     * 插入点赞记录 —— uk 撞（重复点赞）时数据库拒绝、异常上抛给仓储实现。
     */
    @Insert("INSERT INTO note_like (note_id, user_id, create_time) VALUES (#{noteId}, #{userId}, NOW())")
    int insertLike(@Param("noteId") Long noteId, @Param("userId") Long userId);

    /**
     * 删除点赞记录（P32）—— 0 行 = 本未点赞（空操作）。
     */
    @Delete("DELETE FROM note_like WHERE note_id = #{noteId} AND user_id = #{userId}")
    int deleteLike(@Param("noteId") Long noteId, @Param("userId") Long userId);

    /**
     * 当前用户是否已点赞（详情 liked 回填）。
     */
    @Select("SELECT COUNT(*) FROM note_like WHERE note_id = #{noteId} AND user_id = #{userId}")
    int countByNoteAndUser(@Param("noteId") Long noteId, @Param("userId") Long userId);
}
