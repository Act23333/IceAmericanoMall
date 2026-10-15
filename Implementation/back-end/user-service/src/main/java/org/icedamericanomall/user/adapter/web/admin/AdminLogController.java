package org.icedamericanomall.user.adapter.web.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.OperationLogVO;
import org.icedamericanomall.user.app.oplog.OperationLogManager;
import org.icedamericanomall.common.api.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志查询 — 管理后台。
 */
@RestController
@PreAuthorize("@ss.hasPermi('user:admin')")
@RequestMapping("/api/admin/log")
@RequiredArgsConstructor
public class AdminLogController {

    private final OperationLogManager operationLogManager;

    /** 分页查询操作日志（管理后台）。 */
    @GetMapping
    public Result<IPage<OperationLogVO>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(operationLogManager.pageLogs(page, size));
    }
}
