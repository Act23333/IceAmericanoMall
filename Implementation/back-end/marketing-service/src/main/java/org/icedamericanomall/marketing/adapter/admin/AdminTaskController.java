package org.icedamericanomall.marketing.adapter.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.marketing.adapter.request.TaskCreateReq;
import org.icedamericanomall.marketing.adapter.request.TaskUpdateReq;
import org.icedamericanomall.marketing.adapter.response.TaskDefVO;
import org.icedamericanomall.marketing.app.task.TaskAdminManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台后台任务定义接口（V5.13 AD51-AD53，任务运营闭环，ADR-047 表驱动口径的运营出口）。
 *
 * <p>控制器只做参数校验与委托：列表分页 / 创建（code 唯一）/ 编辑（null-safe，code 不可改）
 * 编排下沉到 {@link TaskAdminManager}。
 *
 * <p><b>审计（ADR-032 管道一）</b>：写操作均标注 {@code @OperationLog}，产出单行 JSON
 * 审计日志（logger {@code AUDIT}）；默认不记参数值（PII 安全默认）。
 */
@RestController
// 权限位拆分（5a 批 7，2026-10-18）：每日任务定义管理（任务配置属营销域；GET 在 /api/admin/task，非公开）
@PreAuthorize("@ss.hasPermi('marketing:admin')")
@RequestMapping("/api/admin/task")
@RequiredArgsConstructor
public class AdminTaskController {

    /** 审计模块：营销域任务定义。 */
    private static final String MODULE_MARKETING_TASK = "MARKETING_TASK";

    /** 审计动作：创建任务定义。 */
    private static final String ACTION_CREATE_TASK = "CREATE_TASK";

    /** 审计动作：编辑任务定义。 */
    private static final String ACTION_UPDATE_TASK = "UPDATE_TASK";

    private final TaskAdminManager taskAdminManager;

    /** AD51：分页查询任务定义（status 过滤可选；page 缺省 1，size 缺省 20；code 升序）。 */
    @GetMapping
    public Result<IPage<TaskDefVO>> page(@RequestParam(required = false) Integer status,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return Result.ok(taskAdminManager.page(status, page, size));
    }

    /** AD52：创建任务定义（code 重复 → 400 PARAM_ERROR；type 缺省 1=每日，Phase 2 扩展位）。 */
    @OperationLog(module = MODULE_MARKETING_TASK, action = ACTION_CREATE_TASK, description = "创建任务定义")
    @PostMapping
    public Result<Void> create(@RequestBody @Valid TaskCreateReq req) {
        taskAdminManager.create(req);
        return Result.ok();
    }

    /** AD53：编辑任务定义（code 不可改；不存在 → 1103；rewardPoints 调整立即生效，status=0 停用即触发口失效）。 */
    @OperationLog(module = MODULE_MARKETING_TASK, action = ACTION_UPDATE_TASK, description = "编辑任务定义")
    @PutMapping("/{code}")
    public Result<Void> update(@PathVariable String code, @RequestBody @Valid TaskUpdateReq req) {
        taskAdminManager.update(code, req);
        return Result.ok();
    }
}
