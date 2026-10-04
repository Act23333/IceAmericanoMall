package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.domain.enums.ReviewSortEnum;
import org.icedamericanomall.item.domain.model.review.RatingCount;
import org.icedamericanomall.item.domain.model.review.Review;
import org.icedamericanomall.item.domain.repository.ReviewRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 评价仓储实现 —— MyBatis-Plus 访问 {@code review}/{@code review_like} 表。
 * 回复/追评/点赞的守卫 SQL（{@code ReviewMapper}）原样保留。
 */
@Repository
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepository {

    private final ReviewMapper reviewMapper;

    @Override
    public long countByOrderIdAndUserId(Long orderId, Long userId) {
        return reviewMapper.selectCount(new LambdaQueryWrapper<ReviewPO>()
                .eq(ReviewPO::getOrderId, orderId)
                .eq(ReviewPO::getUserId, userId));
    }

    @Override
    public void save(Review review) {
        ReviewPO po = ReviewRepositoryImpl.toPO(review);
        reviewMapper.insert(po);
        review.setId(po.getId());
    }

    @Override
    public IPage<Review> pageByProduct(Long productId, int page, int size) {
        return reviewMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<ReviewPO>()
                        .eq(ReviewPO::getProductId, productId)
                        .orderByDesc(ReviewPO::getCreateTime))
                .convert(ReviewRepositoryImpl::toDomain);
    }

    @Override
    public IPage<Review> pageFiltered(Long productId, Integer rating, Boolean hasMedia,
                                      String sort, int page, int size) {
        LambdaQueryWrapper<ReviewPO> wrapper = new LambdaQueryWrapper<ReviewPO>()
                .eq(ReviewPO::getProductId, productId);
        if (rating != null) {
            wrapper.eq(ReviewPO::getRating, rating);
        }
        if (hasMedia != null && hasMedia) {
            wrapper.isNotNull(ReviewPO::getImages).ne(ReviewPO::getImages, "");
        }
        ReviewSortEnum sortEnum = ReviewSortEnum.fromCode(sort);
        if (ReviewSortEnum.HIGHEST == sortEnum) {
            wrapper.orderByDesc(ReviewPO::getRating);
        } else if (ReviewSortEnum.LOWEST == sortEnum) {
            wrapper.orderByAsc(ReviewPO::getRating);
        } else {
            wrapper.orderByDesc(ReviewPO::getCreateTime);
        }
        return reviewMapper.selectPage(new Page<>(page, size), wrapper)
                .convert(ReviewRepositoryImpl::toDomain);
    }

    @Override
    public Review findById(Long reviewId) {
        ReviewPO po = reviewMapper.selectById(reviewId);
        return po == null ? null : toDomain(po);
    }

    @Override
    public int countLikeByUser(Long reviewId, Long userId) {
        return reviewMapper.countLikeByUser(reviewId, userId);
    }

    @Override
    public int insertLike(Long reviewId, Long userId) {
        return reviewMapper.insertLike(reviewId, userId);
    }

    @Override
    public int deleteLike(Long reviewId, Long userId) {
        return reviewMapper.deleteLike(reviewId, userId);
    }

    @Override
    public int incrementLike(Long reviewId) {
        return reviewMapper.incrementLike(reviewId);
    }

    @Override
    public int decrementLike(Long reviewId) {
        return reviewMapper.decrementLike(reviewId);
    }

    @Override
    public int addReply(Long reviewId, String content) {
        return reviewMapper.addReply(reviewId, content);
    }

    @Override
    public int addAppend(Long reviewId, Long userId, String content, String mediaUrls) {
        return reviewMapper.addAppend(reviewId, userId, content, mediaUrls);
    }

    @Override
    public List<RatingCount> countByRating(Long productId) {
        return reviewMapper.countByRating(productId).stream()
                .map(row -> new RatingCount(row.getRating(), row.getCount()))
                .toList();
    }

    static ReviewPO toPO(Review review) {
        ReviewPO po = new ReviewPO();
        po.setId(review.getId());
        po.setUserId(review.getUserId());
        po.setProductId(review.getProductId());
        po.setOrderId(review.getOrderId());
        po.setSkuId(review.getSkuId());
        po.setRating(review.getRating());
        po.setContent(review.getContent());
        po.setImages(review.getImages());
        po.setReply(review.getReply());
        po.setReplyTime(review.getReplyTime());
        po.setAppendContent(review.getAppendContent());
        po.setAppendMediaUrls(review.getAppendMediaUrls());
        po.setLikeCount(review.getLikeCount());
        return po;
    }

    static Review toDomain(ReviewPO po) {
        return Review.builder()
                .id(po.getId()).userId(po.getUserId()).productId(po.getProductId())
                .orderId(po.getOrderId()).skuId(po.getSkuId()).rating(po.getRating())
                .content(po.getContent()).images(po.getImages()).reply(po.getReply())
                .replyTime(po.getReplyTime()).appendContent(po.getAppendContent())
                .appendMediaUrls(po.getAppendMediaUrls()).likeCount(po.getLikeCount())
                .build();
    }
}
