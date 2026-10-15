package org.icedamericanomall.item.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.web.QueryModel;
import org.icedamericanomall.item.adapter.request.ReviewAppendReq;
import org.icedamericanomall.item.adapter.request.ReviewCreateReq;
import org.icedamericanomall.item.adapter.request.ReviewFilterReq;
import org.icedamericanomall.item.adapter.request.ReviewReplyReq;
import org.icedamericanomall.item.adapter.response.ReviewSummaryVO;
import org.icedamericanomall.item.adapter.response.ReviewVO;
import org.icedamericanomall.item.app.ReviewManager;
import org.springframework.web.bind.annotation.*;

/**
 * 商品评价 Controller（前台）— V5.0 增强
 *
 * 京东/淘宝标准: 支持发表评价 + 商家回复 + 追评 + 点赞 + 按星级/图片筛选 + 评价摘要
 */
@RestController
@RequestMapping("/api/item/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewManager reviewManager;

    /** 发表评价 */
    @PostMapping
    public Result<ReviewVO> create(@Valid @RequestBody ReviewCreateReq req) {
        return Result.ok(reviewManager.createReview(UserContext.getUserId(), req));
    }

    /** 评价列表 */
    @GetMapping("/product/{productId}")
    public Result<IPage<ReviewVO>> listByProduct(@PathVariable Long productId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewManager.pageByProduct(productId, page, size));
    }

    /** V5.0: 评价筛选 (按星级/有无图片/排序) — query 参数由 {@link ReviewFilterReq} 属性绑定 */
    @GetMapping("/product/{productId}/filter")
    public Result<IPage<ReviewVO>> filter(@PathVariable Long productId, @QueryModel ReviewFilterReq req) {
        return Result.ok(reviewManager.pageByProductFiltered(productId, req.getRating(), req.getHasMedia(),
                req.getSort(), req.getPage(), req.getSize()));
    }

    /** V5.0: 评价摘要 (均分+星级分布) */
    @GetMapping("/product/{productId}/summary")
    public Result<ReviewSummaryVO> summary(@PathVariable Long productId) {
        return Result.ok(reviewManager.reviewSummary(productId));
    }

    /** V5.0: 商家回复评价 — 通知评价作者 */
    @PostMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @Valid @RequestBody ReviewReplyReq req) {
        reviewManager.replyToReview(UserContext.getUserId(), id, req.getContent());
        return Result.ok();
    }

    /** V5.0: 用户追评 */
    @PostMapping("/{id}/append")
    public Result<Void> append(@PathVariable Long id, @Valid @RequestBody ReviewAppendReq req) {
        reviewManager.appendReview(UserContext.getUserId(), id, req.getContent(), req.getMediaUrls());
        return Result.ok();
    }

    /** V5.0: 点赞/取消点赞 — 通知评价作者(仅点赞时)。切换语义，对外仅回传是否成功。 */
    @PostMapping("/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        reviewManager.toggleLike(UserContext.getUserId(), id);
        return Result.ok();
    }
}
