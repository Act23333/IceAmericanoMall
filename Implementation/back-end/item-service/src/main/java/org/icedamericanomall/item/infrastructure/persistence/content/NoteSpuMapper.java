package org.icedamericanomall.item.infrastructure.persistence.content;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 笔记关联商品 Mapper（{@code note_spu}）—— ≤5 条，按业务键（note_id + spu_id）
 * 写入，sort 承载展示顺序；详情装配按 sort 序读取。
 */
public interface NoteSpuMapper extends BaseMapper<NoteSpuPO> {

    /**
     * 写入单条关联（按传入顺序由仓储实现写 sort=0..n-1）。
     */
    @Insert("INSERT INTO note_spu (note_id, spu_id, sort) VALUES (#{noteId}, #{spuId}, #{sort})")
    int insertOne(@Param("noteId") Long noteId, @Param("spuId") Long spuId, @Param("sort") int sort);

    /**
     * 查询笔记关联商品 ID（sort 序；同 sort 按 id 兜底稳定序）。
     */
    @Select("SELECT spu_id FROM note_spu WHERE note_id = #{noteId} ORDER BY sort ASC, id ASC")
    List<Long> selectSpuIdsByNoteId(@Param("noteId") Long noteId);
}
