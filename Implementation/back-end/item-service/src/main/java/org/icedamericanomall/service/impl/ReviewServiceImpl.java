package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.convert.ReviewConverter;
import org.icedamericanomall.domain.dto.ReviewCreateReq;
import org.icedamericanomall.domain.entity.ProductEntity;
import org.icedamericanomall.domain.entity.ReviewEntity;
import org.icedamericanomall.domain.vo.ReviewVO;
import org.icedamericanomall.mapper.ProductMapper;
import org.icedamericanomall.mapper.ReviewMapper;
import org.icedamericanomall.service.ReviewService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品评价服务实现 — V5.0 增强: 回复/追评/点赞
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, ReviewEntity> implements ReviewService {

    private final ReviewConverter reviewConverter;
    private final ProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO createReview(Long userId, ReviewCreateReq req) {
        long count = lambdaQuery()
                .eq(ReviewEntity::getOrderId, req.getOrderId())
                .eq(ReviewEntity::getUserId, userId)
                .count();
        if (count > 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "该订单已评价");
        }
        ReviewEntity entity = reviewConverter.reqToEntity(req);
        entity.setUserId(userId);
        entity.setLikeCount(0);
        save(entity);

        // 更新商品评论数
        ProductEntity product = productMapper.selectById(req.getProductId());
        if (product != null) {
            product.setCommentCount((product.getCommentCount() == null ? 0 : product.getCommentCount()) + 1);
            productMapper.updateById(product);
        }

        return reviewConverter.toVO(entity);
    }

    @Override
    public IPage<ReviewVO> pageByProduct(Long productId, int page, int size) {
        IPage<ReviewEntity> result = lambdaQuery()
                .eq(ReviewEntity::getProductId, productId)
                .orderByDesc(ReviewEntity::getCreateTime)
                .page(new Page<>(page, size));
        return result.convert(r -> enrichVO(reviewConverter.toVO(r)));
    }

    @Override
    public IPage<ReviewVO> pageByProductFiltered(Long productId, Integer rating, Boolean hasMedia,
                                                  String sort, int page, int size) {
        LambdaQueryWrapper<ReviewEntity> wrapper = new LambdaQueryWrapper<ReviewEntity>()
                .eq(ReviewEntity::getProductId, productId);
        if (rating != null) wrapper.eq(ReviewEntity::getRating, rating);
        if (hasMedia != null && hasMedia) wrapper.isNotNull(ReviewEntity::getImages).ne(ReviewEntity::getImages, "");
        if ("highest".equals(sort)) wrapper.orderByDesc(ReviewEntity::getRating);
        else if ("lowest".equals(sort)) wrapper.orderByAsc(ReviewEntity::getRating);
        else wrapper.orderByDesc(ReviewEntity::getCreateTime);

        IPage<ReviewEntity> result = page(new Page<>(page, size), wrapper);
        return result.convert(r -> enrichVO(reviewConverter.toVO(r)));
    }

    // ==================== V5.0: 点赞 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int toggleLike(Long userId, Long reviewId) {
        int exists = baseMapper.countLikeByUser(reviewId, userId);
        if (exists > 0) {
            baseMapper.deleteLike(reviewId, userId);
            baseMapper.decrementLike(reviewId);
            return -1; // 取消点赞
        } else {
            baseMapper.insertLike(reviewId, userId);
            baseMapper.incrementLike(reviewId);
            return 1; // 点赞成功
        }
    }

    // ==================== V5.0: 商家回复 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replyToReview(Long sellerId, Long reviewId, String content) {
        // 校验商家身份: 该评价对应的商品是否属于该商家
        ReviewEntity review = getById(reviewId);
        if (review == null) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "评价不存在");
        ProductEntity product = productMapper.selectById(review.getProductId());
        if (product == null || !product.getSellerId().equals(sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能回复自己店铺商品的评价");
        }
        int rows = baseMapper.addReply(reviewId, content);
        if (rows == 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已回复过该评价");
        log.info("商家回复评价: reviewId={}, sellerId={}", reviewId, sellerId);
    }

    // ==================== V5.0: 用户追评 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendReview(Long userId, Long reviewId, String content, String mediaUrls) {
        int rows = baseMapper.addAppend(reviewId, userId, content, mediaUrls);
        if (rows == 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "追评失败：评价不存在、非本人评价或已追评");
        }
        log.info("用户追评: reviewId={}, userId={}", reviewId, userId);
    }

    // ==================== 私有方法 ====================

    /** V5.0: 填充 likedByMe */
    private ReviewVO enrichVO(ReviewVO vo) {
        try {
            Long userId = UserContext.getUserId();
            if (userId != null) {
                int liked = baseMapper.countLikeByUser(vo.getId(), userId);
                vo.setLikedByMe(liked > 0);
            }
        } catch (Exception ignored) {}
        return vo;
    }
}
