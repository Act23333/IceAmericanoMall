package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.SellerApplicationEntity;
import org.icedAmericanoMall.service.ApplicationService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/application")
@RequiredArgsConstructor
public class AdminApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "0") Integer status) {
        return Result.ok(applicationService.lambdaQuery()
                .eq(SellerApplicationEntity::getStatus, status)
                .orderByDesc(SellerApplicationEntity::getCreateTime)
                .list());
    }

    @PutMapping("/{id}/review")
    public Result<?> review(@PathVariable Long id, @RequestParam Integer status,
                             @RequestParam(required = false) String remark) {
        SellerApplicationEntity app = applicationService.getById(id);
        if (app == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "申请不存在");
        app.setStatus(status);
        app.setAdminRemark(remark);
        applicationService.updateById(app);
        return Result.ok();
    }
}
