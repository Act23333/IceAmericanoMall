package org.icedamericanomall.item.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.utils.IdGenerator;
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
 *
 * <p><b>路径口径（ADR-062 §决策 2/3）</b>：被评价的 SPU 资源段为 {@code spu}，
 * 路径变量显式命名 {@code {spuId}} 且为 <b>32-hex 业务键</b>（入口 {@code requireWire}
 * 形态校验，非法即 400）。评价<b>行主键</b>（{@code /{id}/reply|append|like}）不是实体标识，
 * 保持 {@code Long}（ADR-058 §1 例外：{@code id} = 资源行主键）。
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

    /** 评价列表（{@code {spuId}} 为 SPU 业务键 32-hex）。 */
    @GetMapping("/spu/{spuId}")
    public Result<IPage<ReviewVO>> listBySpu(@PathVariable String spuId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewManager.pageBySpu(IdGenerator.requireWire("spuId", spuId), page, size));
    }

    /** V5.0: 评价筛选 (按星级/有无图片/排序) — query 参数由 {@link ReviewFilterReq} 属性绑定 */
    @GetMapping("/spu/{spuId}/filter")
    public Result<IPage<ReviewVO>> filter(@PathVariable String spuId, @QueryModel ReviewFilterReq req) {
        return Result.ok(reviewManager.pageBySpuFiltered(
                IdGenerator.requireWire("spuId", spuId), req.getRating(), req.getHasMedia(),
                req.getSort(), req.getPage(), req.getSize()));
    }

    /** V5.0: 评价摘要 (均分+星级分布) */
    @GetMapping("/spu/{spuId}/summary")
    public Result<ReviewSummaryVO> summary(@PathVariable String spuId) {
        return Result.ok(reviewManager.reviewSummary(IdGenerator.requireWire("spuId", spuId)));
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
