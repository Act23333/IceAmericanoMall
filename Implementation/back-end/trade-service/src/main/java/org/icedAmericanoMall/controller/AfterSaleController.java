package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.AfterSaleEntity;
import org.icedAmericanoMall.service.AfterSaleService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/after-sale")
@RequiredArgsConstructor
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    @PostMapping
    public Result<AfterSaleEntity> apply(@RequestBody AfterSaleEntity entity) {
        Long userId = UserContext.getUser();
        entity.setUserId(userId);
        entity.setStatus(1);
        if (afterSaleService.existsByOrderAndUser(entity.getOrderNo(), userId)) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已有售后申请");
        }
        afterSaleService.save(entity);
        return Result.ok(entity);
    }

    @GetMapping
    public Result<?> myList(@RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(afterSaleService.pageByUserId(UserContext.getUser(), page, size));
    }
}
