package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.convert.ReviewConverter;
import org.icedamericanomall.domain.dto.ReviewCreateReq;
import org.icedamericanomall.domain.entity.ReviewEntity;
import org.icedamericanomall.domain.vo.ReviewVO;
import org.icedamericanomall.mapper.ReviewMapper;
import org.icedamericanomall.service.ReviewService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品评价服务实现。
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, ReviewEntity> implements ReviewService {

    private final ReviewConverter reviewConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO createReview(Long userId, ReviewCreateReq req) {
        // 一单一评：同一订单同一用户仅可评价一次
        long count = lambdaQuery()
                .eq(ReviewEntity::getOrderId, req.getOrderId())
                .eq(ReviewEntity::getUserId, userId)
                .count();
        if (count > 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "该订单已评价");
        }
        ReviewEntity entity = reviewConverter.reqToEntity(req);
        entity.setUserId(userId);
        save(entity);
        return reviewConverter.toVO(entity);
    }

    @Override
    public IPage<ReviewVO> pageByProduct(Long productId, int page, int size) {
        IPage<ReviewEntity> result = lambdaQuery()
                .eq(ReviewEntity::getProductId, productId)
                .orderByDesc(ReviewEntity::getCreateTime)
                .page(new Page<>(page, size));
        return result.convert(reviewConverter::toVO);
    }
}
