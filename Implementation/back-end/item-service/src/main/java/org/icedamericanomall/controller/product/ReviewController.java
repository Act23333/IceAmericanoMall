package org.icedamericanomall.controller.product;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.domain.entity.ReviewEntity;
import org.icedamericanomall.domain.vo.ReviewVO;
import org.icedamericanomall.domain.dto.ReviewCreateReq;
import org.icedamericanomall.service.ReviewService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 商品评价 Controller — V5.0 增强
 *
 * 京东/淘宝标准: 支持发表评价 + 商家回复 + 追评 + 点赞 + 按星级/图片筛选 + 评价摘要
 */
@Slf4j
@RestController
@RequestMapping("/api/item/review")
public class ReviewController {

    private final ReviewService reviewService;
    private final NotificationClient notificationClient;

    public ReviewController(ReviewService reviewService, NotificationClient notificationClient) {
        this.reviewService = reviewService;
        this.notificationClient = notificationClient;
    }

    /** 发表评价 */
    @PostMapping
    public Result<ReviewVO> create(@RequestBody ReviewCreateReq req) {
        return Result.ok(reviewService.createReview(UserContext.getUserId(), req));
    }

    /** 评价列表 */
    @GetMapping("/product/{productId}")
    public Result<IPage<ReviewVO>> listByProduct(@PathVariable Long productId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewService.pageByProduct(productId, page, size));
    }

    /** V5.0: 评价筛选 (按星级/有无图片/排序) */
    @GetMapping("/product/{productId}/filter")
    public Result<IPage<ReviewVO>> filter(@PathVariable Long productId,
                                           @RequestParam(required = false) Integer rating,
                                           @RequestParam(required = false) Boolean hasMedia,
                                           @RequestParam(defaultValue = "newest") String sort,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewService.pageByProductFiltered(productId, rating, hasMedia, sort, page, size));
    }

    /** V5.0: 评价摘要 (均分+星级分布) — 直接从分页数据计算 */
    @GetMapping("/product/{productId}/summary")
    public Result<Map<String, Object>> summary(@PathVariable Long productId) {
        var page = reviewService.pageByProduct(productId, 1, 1000);
        var records = page.getRecords();
        double avg = records.stream().mapToInt(ReviewVO::getRating).average().orElse(0);
        java.util.Map<Integer, Long> dist = new java.util.HashMap<>();
        for (int i = 1; i <= 5; i++) {
            final int star = i;
            dist.put(i, records.stream().filter(r -> r.getRating() == star).count());
        }
        return Result.ok(Map.of(
                "averageRating", Math.round(avg * 10) / 10.0,
                "totalCount", records.size(),
                "distribution", dist
        ));
    }

    /** V5.0: 商家回复评价 — 通知评价作者 */
    @PostMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long sellerId = UserContext.getUserId();
        reviewService.replyToReview(sellerId, id, body.get("content"));
        // 发通知给评价作者
        ReviewEntity r = reviewService.getById(id);
        if (r != null) fireNotification(r.getUserId(), sellerId, "REPLY",
                "商家回复了你的评价", body.get("content"), "/product/" + r.getProductId());
        return Result.ok();
    }

    /** V5.0: 用户追评 */
    @PostMapping("/{id}/append")
    public Result<Void> append(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        reviewService.appendReview(userId, id, body.get("content"), body.get("mediaUrls"));
        return Result.ok();
    }

    /** V5.0: 点赞/取消点赞 — 通知评价作者(仅点赞时) */
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        int result = reviewService.toggleLike(userId, id);
        if (result > 0) {
            ReviewEntity r = reviewService.getById(id);
            if (r != null) fireNotification(r.getUserId(), userId, "LIKE",
                    "有人赞了你的评价", null, "/product/" + r.getProductId());
        }
        return Result.ok(Map.of("action", result > 0 ? "liked" : "unliked"));
    }

    // ==================== 通知触发 ====================

    private void fireNotification(Long recipientId, Long senderId, String type,
                                   String title, String content, String linkUrl) {
        try {
            notificationClient.send(Map.of(
                    "recipientId", recipientId,
                    "senderId", senderId,
                    "senderName", "",  // ReviewController 层不查用户名，由 user-service 的 service 层懒查
                    "senderAvatar", "",
                    "type", type,
                    "title", title,
                    "content", content != null ? content : "",
                    "linkUrl", linkUrl != null ? linkUrl : ""
            ));
        } catch (Exception e) {
            log.warn("发送通知失败(type={}, recipient={}): {}", type, recipientId, e.getMessage());
        }
    }
}
