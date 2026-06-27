package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.SellerApplicationEntity;
import org.icedAmericanoMall.service.ApplicationService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seller/apply")
@RequiredArgsConstructor
public class SellerApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    public Result<SellerApplicationEntity> apply(@RequestBody SellerApplicationEntity entity) {
        Long userId = UserContext.getUser();
        if (applicationService.hasPendingApplication(userId)) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已有审核中的申请");
        }
        entity.setUserId(userId);
        entity.setStatus(0);
        applicationService.save(entity);
        return Result.ok(entity);
    }

    @GetMapping
    public Result<SellerApplicationEntity> myApplication() {
        return Result.ok(applicationService.getLatestApplication(UserContext.getUser()));
    }
}
