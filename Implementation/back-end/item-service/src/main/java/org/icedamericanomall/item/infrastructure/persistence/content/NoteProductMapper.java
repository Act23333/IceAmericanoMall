package org.icedamericanomall.item.infrastructure.persistence.content;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 笔记关联商品 Mapper（{@code note_product}）—— ≤5 条，按业务键（note_id + product_id）
 * 写入，sort 承载展示顺序；详情装配按 sort 序读取。
 */
public interface NoteProductMapper extends BaseMapper<NoteProductPO> {

    /**
     * 写入单条关联（按传入顺序由仓储实现写 sort=0..n-1）。
     */
    @Insert("INSERT INTO note_product (note_id, product_id, sort) VALUES (#{noteId}, #{productId}, #{sort})")
    int insertOne(@Param("noteId") Long noteId, @Param("productId") Long productId, @Param("sort") int sort);

    /**
     * 查询笔记关联商品 ID（sort 序；同 sort 按 id 兜底稳定序）。
     */
    @Select("SELECT product_id FROM note_product WHERE note_id = #{noteId} ORDER BY sort ASC, id ASC")
    List<Long> selectProductIdsByNoteId(@Param("noteId") Long noteId);
}
