package org.icedamericanomall.item.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.item.domain.model.review.Review;

import java.util.List;

/**
 * 商品评价仓储（领域端口）—— 实现方：{@code infrastructure.persistence.ReviewRepositoryImpl}。
 *
 * <p>分页返回 MyBatis-Plus {@code IPage} 信封：评价列表是既有 HTTP 契约（有界务实豁免）。
 */
public interface ReviewRepository {

    /** 同订单同用户是否已有评价（重复评价守卫）。 */
    long countByOrderNoAndUserId(String orderNo, Long userId);

    /** 新增评价。 */
    void save(Review review);

    /** 商品评价分页（创建时间倒序）。 */
    IPage<Review> pageBySpu(Long spuId, int page, int size);

    /** 商品评价分页（星级/有图过滤 + 排序归一）。 */
    IPage<Review> pageFiltered(Long spuId, Integer rating, Boolean hasMedia,
                               String sort, int page, int size);

    /** 按 ID 载入（可为 null）。 */
    Review findById(Long reviewId);

    /** 点赞计数（本人是否已点）。 */
    int countLikeByUser(Long reviewId, Long userId);

    /** 插入点赞记录。 */
    int insertLike(Long reviewId, Long userId);

    /** 删除点赞记录。 */
    int deleteLike(Long reviewId, Long userId);

    /** 点赞数 +1 / -1。 */
    int incrementLike(Long reviewId);

    int decrementLike(Long reviewId);

    /** 商家回复（reply IS NULL 守卫在 SQL 内）；0 行 = 已回复过。 */
    int addReply(Long reviewId, String content);

    /** 用户追评（本人 + 未追评守卫在 SQL 内）；0 行 = 追评失败。 */
    int addAppend(Long reviewId, Long userId, String content, String mediaUrls);

    /** 星级分组计数（GROUP BY rating；脏数据由应用层忽略）。 */
    List<org.icedamericanomall.item.domain.model.review.RatingCount> countByRating(Long spuId);
}
