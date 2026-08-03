package org.icedamericanomall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.domain.dto.ReviewCreateReq;
import org.icedamericanomall.domain.entity.ReviewEntity;
import org.icedamericanomall.domain.vo.ReviewVO;

/**
 * 商品评价服务 — V5.0 增强: 回复/追评/点赞
 */
public interface ReviewService extends IService<ReviewEntity> {

    ReviewVO createReview(Long userId, ReviewCreateReq req);

    IPage<ReviewVO> pageByProduct(Long productId, int page, int size);

    IPage<ReviewVO> pageByProductFiltered(Long productId, Integer rating, Boolean hasMedia,
                                           String sort, int page, int size);

    /** V5.0: 商家回复评价 */
    void replyToReview(Long sellerId, Long reviewId, String content);

    /** V5.0: 用户追评 */
    void appendReview(Long userId, Long reviewId, String content, String mediaUrls);

    /** V5.0: 点赞/取消点赞 */
    int toggleLike(Long userId, Long reviewId);
}
