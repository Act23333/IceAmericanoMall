package org.icedamericanomall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.icedamericanomall.domain.entity.ReviewEntity;

@Mapper
public interface ReviewMapper extends BaseMapper<ReviewEntity> {

    /** V5.0: 点赞 — 原子递增 like_count + 插入 review_like */
    @Update("UPDATE review SET like_count = like_count + 1 WHERE id = #{reviewId}")
    int incrementLike(@Param("reviewId") Long reviewId);

    /** V5.0: 取消点赞 — 原子递减 + 删除关联 */
    @Update("UPDATE review SET like_count = GREATEST(0, like_count - 1) WHERE id = #{reviewId}")
    int decrementLike(@Param("reviewId") Long reviewId);

    /** V5.0: 查用户是否已点赞 */
    @Select("SELECT COUNT(*) FROM review_like WHERE review_id = #{reviewId} AND user_id = #{userId}")
    int countLikeByUser(@Param("reviewId") Long reviewId, @Param("userId") Long userId);

    /** V5.0: 插入点赞关联 */
    @Insert("INSERT INTO review_like (review_id, user_id, create_time) VALUES (#{reviewId}, #{userId}, NOW())")
    int insertLike(@Param("reviewId") Long reviewId, @Param("userId") Long userId);

    /** V5.0: 删除点赞关联 */
    @Delete("DELETE FROM review_like WHERE review_id = #{reviewId} AND user_id = #{userId}")
    int deleteLike(@Param("reviewId") Long reviewId, @Param("userId") Long userId);

    /** V5.0: 商家回复 */
    @Update("UPDATE review SET reply = #{content}, reply_time = NOW() WHERE id = #{reviewId} AND reply IS NULL")
    int addReply(@Param("reviewId") Long reviewId, @Param("content") String content);

    /** V5.0: 用户追评 */
    @Update("UPDATE review SET append_content = #{content}, append_media_urls = #{mediaUrls}, append_time = NOW() WHERE id = #{reviewId} AND user_id = #{userId} AND append_content IS NULL")
    int addAppend(@Param("reviewId") Long reviewId, @Param("userId") Long userId,
                  @Param("content") String content, @Param("mediaUrls") String mediaUrls);
}
