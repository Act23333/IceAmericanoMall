package org.icedamericanomall.item.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.TaskClient;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.dto.TaskCompleteCO;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.item.adapter.assembler.ReviewConverter;
import org.icedamericanomall.item.adapter.request.ReviewCreateReq;
import org.icedamericanomall.item.adapter.response.ReviewSummaryVO;
import org.icedamericanomall.item.adapter.response.ReviewVO;
import org.icedamericanomall.item.domain.model.product.Product;
import org.icedamericanomall.item.domain.model.review.Review;
import org.icedamericanomall.item.domain.repository.ProductRepository;
import org.icedamericanomall.item.domain.repository.ReviewRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评价编排（应用层）—— 评价用例（发布/分页/汇总/回复/追评/点赞）+ 站内信通知触发
 * + 每日评价任务旁路触发（F9，ADR-047）。
 *
 * <p>跨服务调用（notification-service / marketing-service）只允许出现在本层；
 * 回复/追评/点赞的守卫 SQL（reply IS NULL、本人追评）在仓储实现内。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewManager {

    private static final String NOTIFY_TYPE_REPLY = "REPLY";
    private static final String NOTIFY_TYPE_LIKE = "LIKE";
    private static final String NOTIFY_TITLE_REPLY = "商家回复了你的评价";
    private static final String NOTIFY_TITLE_LIKE = "有人赞了你的评价";
    private static final String PRODUCT_LINK_PREFIX = "/product/";
    /** F9 每日任务编码（ADR-047 task_def.code seed 常量，对应「每日发表 1 条评价」）。 */
    private static final String TASK_CODE_DAILY_REVIEW = "DAILY_REVIEW";

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ReviewConverter reviewConverter;
    private final NotificationClient notificationClient;
    private final TaskClient taskClient;

    /** 发表评价（同订单同用户去重），累加商品评论数，并在事务提交后触发每日评价任务（F9）。 */
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO createReview(Long userId, ReviewCreateReq req) {
        String orderNo = normalizeOrderNo(req.getOrderNo());
        if (reviewRepository.countByOrderNoAndUserId(orderNo, userId) > 0) {
            throw new BizException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        Review review = Review.post(userId, req.getProductId(), orderNo, req.getSkuId(),
                req.getRating(), req.getContent(), req.getImages());
        reviewRepository.save(review);
        productRepository.incrementCommentCount(req.getProductId());
        triggerDailyReviewTask(userId);
        return reviewConverter.toVO(review);
    }

    /**
     * 订单号归一（ADR-050 三批：跨域引用收敛为业务键）：必须为 32 位 hex/36 位连字符 UUID
     * （来源 {@code OrderVO.orderNo}，现有契约缺口一并修复——此前 orderId(Long) 前端无从获得），
     * 非法输入 fail-fast {@code PARAM_ERROR}。
     */
    private static String normalizeOrderNo(String orderNo) {
        try {
            return IdGenerator.toHexString(IdGenerator.toBytes(orderNo));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "orderNo 必须为 32 位 hex 或 36 位连字符 UUID: " + orderNo);
        }
    }

    /** 商品评价分页（创建时间倒序，带 likedByMe 回填）。 */
    public IPage<ReviewVO> pageByProduct(Long productId, int page, int size) {
        return reviewRepository.pageByProduct(productId, page, size)
                .convert(r -> enrichVO(reviewConverter.toVO(r)));
    }

    /** 商品评价分页（星级/有图过滤 + 排序归一）。 */
    public IPage<ReviewVO> pageByProductFiltered(Long productId, Integer rating, Boolean hasMedia,
                                                 String sort, int page, int size) {
        return reviewRepository.pageFiltered(productId, rating, hasMedia, sort, page, size)
                .convert(r -> enrichVO(reviewConverter.toVO(r)));
    }

    /**
     * 评价汇总 —— 一次 GROUP BY 聚合得到星级分布；rating 为 null 或超出 1~5 的
     * 历史脏数据被忽略，不参与均分。
     */
    public ReviewSummaryVO reviewSummary(Long productId) {
        long total = 0L;
        long weightedSum = 0L;
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = Review.MIN_RATING; star <= Review.MAX_RATING; star++) {
            distribution.put(star, 0L);
        }
        List<org.icedamericanomall.item.domain.model.review.RatingCount> rows =
                reviewRepository.countByRating(productId);
        if (rows != null) {
            for (org.icedamericanomall.item.domain.model.review.RatingCount row : rows) {
                Integer rating = row.rating();
                if (rating == null || rating < Review.MIN_RATING || rating > Review.MAX_RATING) {
                    continue;
                }
                long count = row.countOrZero();
                distribution.merge(rating, count, Long::sum);
                total += count;
                weightedSum += (long) rating * count;
            }
        }
        ReviewSummaryVO summary = new ReviewSummaryVO();
        summary.setTotalCount(total);
        summary.setDistribution(distribution);
        summary.setAverageScore(total == 0L ? 0.0 : Math.round(weightedSum * 10.0 / total) / 10.0);
        return summary;
    }

    /**
     * 点赞/取消点赞（仓储幂等矩阵），点赞成功时通知评价作者。
     *
     * @return 1=点赞成功，-1=取消点赞
     */
    @Transactional(rollbackFor = Exception.class)
    public int toggleLike(Long userId, Long reviewId) {
        int exists = reviewRepository.countLikeByUser(reviewId, userId);
        int result;
        if (exists > 0) {
            reviewRepository.deleteLike(reviewId, userId);
            reviewRepository.decrementLike(reviewId);
            result = -1;
        } else {
            reviewRepository.insertLike(reviewId, userId);
            reviewRepository.incrementLike(reviewId);
            result = 1;
        }
        if (result > 0) {
            Review review = reviewRepository.findById(reviewId);
            if (review != null) {
                notifyReviewAuthor(review.getUserId(), userId, NOTIFY_TYPE_LIKE,
                        NOTIFY_TITLE_LIKE, null, review.getProductId());
            }
        }
        return result;
    }

    /** 商家回复评价（商品归属校验），成功后通知评价作者。 */
    @Transactional(rollbackFor = Exception.class)
    public void replyToReview(Long sellerId, Long reviewId, String content) {
        Review review = reviewRepository.findById(reviewId);
        if (review == null) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "评价不存在");
        }
        Product product = productRepository.findById(review.getProductId());
        if (product == null || !product.getSellerId().equals(sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能回复自己店铺商品的评价");
        }
        if (reviewRepository.addReply(reviewId, content) == 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已回复过该评价");
        }
        log.info("商家回复评价: reviewId={}, sellerId={}", reviewId, sellerId);
        notifyReviewAuthor(review.getUserId(), sellerId, NOTIFY_TYPE_REPLY,
                NOTIFY_TITLE_REPLY, content, review.getProductId());
    }

    /** 用户追评（非本人/已追评由仓储守卫拒绝）。 */
    @Transactional(rollbackFor = Exception.class)
    public void appendReview(Long userId, Long reviewId, String content, String mediaUrls) {
        if (reviewRepository.addAppend(reviewId, userId, content, mediaUrls) == 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "追评失败：评价不存在、非本人评价或已追评");
        }
        log.info("用户追评: reviewId={}, userId={}", reviewId, userId);
    }

    /** 评价作者回填 likedByMe（失败不影响评价主流程，仅记日志）。 */
    private ReviewVO enrichVO(ReviewVO vo) {
        try {
            Long userId = UserContext.getUserId();
            if (userId != null) {
                vo.setLikedByMe(reviewRepository.countLikeByUser(vo.getId(), userId) > 0);
            }
        } catch (Exception e) {
            log.warn("填充点赞状态失败: reviewId={}, cause={}", vo.getId(), e.getMessage());
        }
        return vo;
    }

    /**
     * 发送站内信（经 notification-service 落库）；失败只记日志，不影响评价主流程。
     */
    private void notifyReviewAuthor(Long recipientId, Long senderId, String type,
                                    String title, String content, Long productId) {
        try {
            InAppMessageSendReqCO message = new InAppMessageSendReqCO();
            message.setRecipientId(recipientId);
            message.setSenderId(senderId);
            // 不查用户名：senderName 由前端按 senderId 渲染（昵称兜底已随边界重构移除）
            message.setSenderName("");
            message.setSenderAvatar("");
            message.setType(type);
            message.setTitle(title);
            message.setContent(content != null ? content : "");
            message.setLinkUrl(productId != null ? PRODUCT_LINK_PREFIX + productId : "");
            notificationClient.sendInAppMessage(message);
        } catch (Exception e) {
            log.warn("发送评价通知失败(type={}, recipient={}): {}", type, recipientId, e.getMessage());
        }
    }

    /**
     * 每日评价任务旁路触发（F9 DAILY_REVIEW，ADR-047）—— 评价落库成功后触发。
     *
     * <p><b>事务纪律（硬约束：Feign 严禁进事务）</b>：调用点处于 {@code createReview} 的
     * 写事务内，故对齐 {@link ProductRecommendManager} 摄取先例 —— 有活动事务时注册
     * {@code afterCommit} 回调（回滚即不触发，提交后才远程调用），无活动事务直接发送。
     */
    private void triggerDailyReviewTask(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            completeDailyReviewTask(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                completeDailyReviewTask(userId);
            }
        });
    }

    /** 任务触发（fail-open）：降级返回 {@code completed=false}（返回值忽略）或异常均只记日志，评价主流程零感知。 */
    private void completeDailyReviewTask(Long userId) {
        try {
            taskClient.complete(new TaskCompleteCO(userId, TASK_CODE_DAILY_REVIEW));
        } catch (Exception e) {
            log.warn("每日任务触发失败（不阻断评价主流程）: userId={}", userId, e);
        }
    }
}
