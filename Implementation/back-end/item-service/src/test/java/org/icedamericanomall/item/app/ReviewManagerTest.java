package org.icedamericanomall.item.app;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.adapter.assembler.ReviewConverter;
import org.icedamericanomall.item.adapter.request.ReviewCreateReq;
import org.icedamericanomall.item.adapter.response.ReviewSummaryVO;
import org.icedamericanomall.item.adapter.response.ReviewVO;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.model.review.Review;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.icedamericanomall.item.domain.repository.ReviewRepository;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.TaskClient;
import org.icedamericanomall.dto.TaskCompleteCO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReviewManager 单元测试 —— 评价发布去重、回复归属校验、追评守卫、汇总与通知触发。
 * （原 ReviewServiceImplTest 语义迁移：查询沉仓储、编排归本类。）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewManager 编排单元测试")
class ReviewManagerTest {

    /** 测试用订单号（32 位 hex 契约，ADR-050 三批）。 */
    private static final String ORDER_NO = "aaaaaaaa000070008000000000000077";

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private SpuRepository spuRepository;

    @Mock
    private ReviewConverter reviewConverter;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private TaskClient taskClient;

    private ReviewManager reviewManager;

    @BeforeEach
    void setUp() {
        reviewManager = new ReviewManager(reviewRepository, spuRepository,
                reviewConverter, notificationClient, taskClient);
    }

    private Review review(Long userId, Long spuId) {
        return Review.post(userId, spuId, ORDER_NO, 2L, 5, "很好", "img.jpg");
    }

    private ReviewVO vo(Long id) {
        ReviewVO vo = new ReviewVO();
        vo.setId(id);
        return vo;
    }

    private ReviewCreateReq reviewReq() {
        ReviewCreateReq req = new ReviewCreateReq();
        req.setOrderNo(ORDER_NO);
        req.setSpuId(10L);
        req.setSkuId(2L);
        req.setRating(5);
        req.setContent("很好");
        req.setImages("img.jpg");
        return req;
    }

    // ==================== createReview ====================

    @Test
    @DisplayName("createReview — 同订单同用户重复评价 → REVIEW_ALREADY_EXISTS")
    void shouldReject_whenAlreadyReviewed() {
        ReviewCreateReq req = new ReviewCreateReq();
        req.setOrderNo(ORDER_NO);
        req.setSpuId(10L);
        when(reviewRepository.countByOrderNoAndUserId(ORDER_NO, 1L)).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> reviewManager.createReview(1L, req));
        assertEquals(ErrorCode.REVIEW_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("createReview — 发布成功并累加商品评论数")
    void shouldSaveAndIncrementCommentCount() {
        ReviewCreateReq req = new ReviewCreateReq();
        req.setOrderNo(ORDER_NO);
        req.setSpuId(10L);
        req.setSkuId(2L);
        req.setRating(5);
        req.setContent("很好");
        req.setImages("img.jpg");
        when(reviewRepository.countByOrderNoAndUserId(ORDER_NO, 1L)).thenReturn(0L);
        when(reviewConverter.toVO(any())).thenReturn(vo(1L));

        reviewManager.createReview(1L, req);

        verify(reviewRepository).save(any(Review.class));
        verify(spuRepository).incrementCommentCount(10L);
    }

    @Test
    @DisplayName("createReview — F9 落库后触发每日评价任务：DAILY_REVIEW 携带 userId（ADR-047）")
    void shouldTriggerDailyReviewTask_whenReviewCreated() {
        when(reviewRepository.countByOrderNoAndUserId(ORDER_NO, 1L)).thenReturn(0L);
        when(reviewConverter.toVO(any())).thenReturn(vo(1L));

        reviewManager.createReview(1L, reviewReq());

        ArgumentCaptor<TaskCompleteCO> captor = ArgumentCaptor.forClass(TaskCompleteCO.class);
        verify(taskClient).complete(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals("DAILY_REVIEW", captor.getValue().getTaskCode());
    }

    @Test
    @DisplayName("createReview — 活动事务内注册 afterCommit：提交前不触发，提交后才触发（Feign 不入事务）")
    void shouldTriggerTaskOnlyAfterCommit_whenTransactionActive() {
        when(reviewRepository.countByOrderNoAndUserId(ORDER_NO, 1L)).thenReturn(0L);
        when(reviewConverter.toVO(any())).thenReturn(vo(1L));
        TransactionSynchronizationManager.initSynchronization();
        try {
            reviewManager.createReview(1L, reviewReq());

            verify(taskClient, never()).complete(any());

            for (TransactionSynchronization sync
                    : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            ArgumentCaptor<TaskCompleteCO> captor = ArgumentCaptor.forClass(TaskCompleteCO.class);
            verify(taskClient).complete(captor.capture());
            assertEquals(1L, captor.getValue().getUserId());
            assertEquals("DAILY_REVIEW", captor.getValue().getTaskCode());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("createReview — 任务触发异常 fail-open：评价落库主流程不受影响")
    void shouldNotBlockReview_whenTaskTriggerFails() {
        when(reviewRepository.countByOrderNoAndUserId(ORDER_NO, 1L)).thenReturn(0L);
        when(reviewConverter.toVO(any())).thenReturn(vo(1L));
        doThrow(new RuntimeException("marketing-service down")).when(taskClient).complete(any());

        assertDoesNotThrow(() -> reviewManager.createReview(1L, reviewReq()));

        verify(reviewRepository).save(any(Review.class));
        verify(spuRepository).incrementCommentCount(10L);
    }

    // ==================== reviewSummary ====================

    @Test
    @DisplayName("reviewSummary — 星级分布与均分（脏数据忽略）")
    void shouldSummarize_withDistribution() {
        when(reviewRepository.countByRating(10L)).thenReturn(List.of(
                new org.icedamericanomall.item.domain.model.review.RatingCount(5, 3L),
                new org.icedamericanomall.item.domain.model.review.RatingCount(4, 1L),
                new org.icedamericanomall.item.domain.model.review.RatingCount(9, 7L),
                new org.icedamericanomall.item.domain.model.review.RatingCount(null, 2L)));

        ReviewSummaryVO summary = reviewManager.reviewSummary(10L);

        assertEquals(4L, summary.getTotalCount());
        assertEquals(0L, summary.getDistribution().get(1));
        assertEquals(3L, summary.getDistribution().get(5));
        assertEquals(5, summary.getDistribution().size());
        assertFalse(summary.getDistribution().containsKey(9), "越界脏数据不参与分布");
        assertEquals(4.8, summary.getAverageScore(), 0.001);
    }

    @Test
    @DisplayName("reviewSummary — 无评价数据：均分 0、分布全 0")
    void shouldSummarizeZeros_whenNoReviews() {
        when(reviewRepository.countByRating(10L)).thenReturn(List.of());

        ReviewSummaryVO summary = reviewManager.reviewSummary(10L);

        assertEquals(0L, summary.getTotalCount());
        assertEquals(0.0, summary.getAverageScore(), 0.001);
        assertEquals(5, summary.getDistribution().size());
        assertTrue(summary.getDistribution().values().stream().allMatch(c -> c == 0L));
    }

    @Test
    @DisplayName("reviewSummary — mapper 返回 null 时不抛 NPE（fail-open）")
    void shouldNotThrow_whenCountByRatingReturnsNull() {
        when(reviewRepository.countByRating(10L)).thenReturn(null);

        ReviewSummaryVO summary = reviewManager.reviewSummary(10L);

        assertEquals(0L, summary.getTotalCount());
        assertEquals(0.0, summary.getAverageScore(), 0.001);
    }

    // ==================== replyToReview ====================

    @Test
    @DisplayName("replyToReview — 非本人店铺商品 → FORBIDDEN")
    void shouldThrowForbidden_whenReplyingOthersSpu() {
        Review r = review(1L, 10L);
        when(reviewRepository.findById(5L)).thenReturn(r);
        when(spuRepository.findById(10L)).thenReturn(Spu.publish(999L, 1L, null, "p", null, null, null));

        BizException ex = assertThrows(BizException.class,
                () -> reviewManager.replyToReview(100L, 5L, "回复"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("replyToReview — 已回复过 → 重复回复被拒")
    void shouldReject_whenAlreadyReplied() {
        Review r = review(1L, 10L);
        when(reviewRepository.findById(5L)).thenReturn(r);
        when(spuRepository.findById(10L)).thenReturn(Spu.publish(100L, 1L, null, "p", null, null, null));
        when(reviewRepository.addReply(5L, "回复")).thenReturn(0);

        assertThrows(BizException.class, () -> reviewManager.replyToReview(100L, 5L, "回复"));
    }

    // ==================== appendReview ====================

    @Test
    @DisplayName("appendReview — 守卫 SQL 0 行（非本人/已追评）→ 拒绝")
    void shouldReject_whenAppendGuardFails() {
        when(reviewRepository.addAppend(5L, 1L, "追评", null)).thenReturn(0);

        assertThrows(BizException.class,
                () -> reviewManager.appendReview(1L, 5L, "追评", null));
    }

    // ==================== toggleLike ====================

    @Test
    @DisplayName("toggleLike — 点赞成功 +1 并通知评价作者")
    void shouldNotify_whenLiked() {
        Review r = review(1L, 10L);
        when(reviewRepository.countLikeByUser(5L, 2L)).thenReturn(0);
        when(reviewRepository.findById(5L)).thenReturn(r);

        int result = reviewManager.toggleLike(2L, 5L);

        assertEquals(1, result);
        verify(reviewRepository).insertLike(5L, 2L);
        verify(reviewRepository).incrementLike(5L);
        verify(notificationClient).sendInAppMessage(any());
    }

    @Test
    @DisplayName("toggleLike — 取消点赞 -1 且不通知")
    void shouldNotNotify_whenUnliked() {
        when(reviewRepository.countLikeByUser(5L, 2L)).thenReturn(1);

        int result = reviewManager.toggleLike(2L, 5L);

        assertEquals(-1, result);
        verify(reviewRepository).deleteLike(5L, 2L);
        verify(notificationClient, never()).sendInAppMessage(any());
    }

    @Test
    @DisplayName("createReview — orderNo 非 hex（如旧 orderId 形态）→ PARAM_ERROR fail-fast")
    void shouldRejectInvalidOrderNo_whenNotHex() {
        ReviewCreateReq req = new ReviewCreateReq();
        req.setOrderNo("77");
        assertThrows(BizException.class, () -> reviewManager.createReview(1L, req));
    }

}
