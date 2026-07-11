package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.dto.ReviewCreateReq;
import org.icedAmericanoMall.domain.entity.ReviewEntity;
import org.icedAmericanoMall.domain.vo.ReviewVO;

/**
 * 商品评价服务 —— 一单一评校验、按商品分页查询。
 */
public interface ReviewService extends IService<ReviewEntity> {

    ReviewVO createReview(Long userId, ReviewCreateReq req);

    IPage<ReviewVO> pageByProduct(Long productId, int page, int size);
}
