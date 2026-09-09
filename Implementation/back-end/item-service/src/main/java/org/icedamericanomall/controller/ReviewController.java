package org.icedamericanomall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.dto.ReviewCreateReq;
import org.icedamericanomall.domain.vo.ReviewVO;
import org.icedamericanomall.service.ReviewService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/item/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public Result<ReviewVO> create(@RequestBody ReviewCreateReq req) {
        return Result.ok(reviewService.createReview(UserContext.getUserId(), req));
    }

    @GetMapping("/product/{productId}")
    public Result<IPage<ReviewVO>> listByProduct(@PathVariable Long productId,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewService.pageByProduct(productId, page, size));
    }
}
