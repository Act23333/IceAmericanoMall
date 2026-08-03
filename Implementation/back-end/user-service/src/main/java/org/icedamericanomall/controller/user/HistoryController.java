package org.icedamericanomall.controller.user;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.service.HistoryService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @PostMapping
    public Result<?> record(@RequestParam Long productId) {
        historyService.record(UserContext.getUserId(), productId);
        return Result.ok();
    }

    @GetMapping
    public Result<List<Long>> list(@RequestParam(defaultValue = "20") int size) {
        return Result.ok(historyService.list(UserContext.getUserId(), size));
    }

    @DeleteMapping
    public Result<?> clear() {
        historyService.clear(UserContext.getUserId());
        return Result.ok();
    }
}
