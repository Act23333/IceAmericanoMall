package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.vo.OperationLogVO;
import org.icedAmericanoMall.service.OperationLogService;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志查询 — 管理后台。
 */
@RestController
@RequestMapping("/api/admin/log")
@RequiredArgsConstructor
public class AdminLogController {

    private final OperationLogService operationLogService;

    @GetMapping
    public Result<IPage<OperationLogVO>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(operationLogService.pageLogs(page, size));
    }
}
