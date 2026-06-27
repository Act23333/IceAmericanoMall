package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.AfterSaleEntity;
import org.icedAmericanoMall.service.AfterSaleService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/after-sale")
@RequiredArgsConstructor
public class AdminAfterSaleController {

    private final AfterSaleService afterSaleService;

    @GetMapping
    public Result<?> list(@RequestParam(required = false) Integer status,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(afterSaleService.pageByStatus(status, page, size));
    }

    @PutMapping("/{id}/review")
    public Result<?> review(@PathVariable Long id, @RequestParam Integer status,
                             @RequestParam(required = false) String remark) {
        AfterSaleEntity entity = afterSaleService.getById(id);
        if (entity == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "售后申请不存在");
        entity.setStatus(status);
        entity.setAdminRemark(remark);
        afterSaleService.updateById(entity);
        return Result.ok();
    }
}
