package org.icedamericanomall.trade.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.trade.adapter.request.AfterSaleApplyReq;
import org.icedamericanomall.trade.adapter.response.AfterSaleVO;
import org.icedamericanomall.trade.app.aftersale.AfterSaleManager;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/after-sale")
@RequiredArgsConstructor
public class AfterSaleController {

    private final AfterSaleManager afterSaleManager;

    @PostMapping
    public Result<AfterSaleVO> apply(@Valid @RequestBody AfterSaleApplyReq req) {
        return Result.ok(afterSaleManager.apply(UserContext.getUserId(), req));
    }

    @GetMapping
    public Result<IPage<AfterSaleVO>> myList(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(afterSaleManager.listMy(UserContext.getUserId(), page, size));
    }
}
