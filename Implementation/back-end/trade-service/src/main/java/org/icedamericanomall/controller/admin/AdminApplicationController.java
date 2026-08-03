package org.icedamericanomall.controller.admin;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.convert.TradeConverter;
import org.icedamericanomall.domain.vo.SellerApplicationVO;
import org.icedamericanomall.service.ApplicationService;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/application")
@RequiredArgsConstructor
public class AdminApplicationController {

    private final ApplicationService applicationService;
    private final TradeConverter tradeConverter;

    @GetMapping
    public Result<List<SellerApplicationVO>> list(@RequestParam(defaultValue = "0") Integer status) {
        return Result.ok(applicationService.listByStatus(status).stream()
                .map(tradeConverter::toVO).toList());
    }

    @PutMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id, @RequestParam Integer status,
                               @RequestParam(required = false) String remark) {
        applicationService.review(id, status, remark);
        return Result.ok();
    }
}
