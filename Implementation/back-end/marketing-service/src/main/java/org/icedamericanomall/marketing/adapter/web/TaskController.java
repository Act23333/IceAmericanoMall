package org.icedamericanomall.marketing.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.marketing.adapter.response.DailyTaskVO;
import org.icedamericanomall.marketing.app.task.TaskManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务中心公开端点（适配层）—— {@code GET /api/task/daily}（契约 §4.10.3 M14，V5.10）。
 *
 * <p>适配层子包按受众分组（web/admin/internal，ADR-023），本类为前台（🔒 当前用户）入口；
 * 用户身份取自 {@link UserContext}（网关注入，禁止信任前端传参）。
 */
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskManager taskManager;

    /** 今日任务列表（全部启用任务 + 当日完成状态）。 */
    @GetMapping("/daily")
    public Result<List<DailyTaskVO>> dailyTasks() {
        return Result.ok(taskManager.dailyTasks(UserContext.getUserId()));
    }
}
