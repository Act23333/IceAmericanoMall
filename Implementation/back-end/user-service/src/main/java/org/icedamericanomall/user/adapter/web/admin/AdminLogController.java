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
// 权限位拆分（5a 批 8，2026-10-18）：审计日志查询独立成位 audit:read。
// **不挂 user:read** —— 读审计（谁做了什么）与读用户资料是两件事，前者的信息更敏感。
@PreAuthorize("@ss.hasPermi('audit:read')")
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
