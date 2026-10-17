package org.icedamericanomall.marketing.adapter.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.TaskCompleteCO;
import org.icedamericanomall.dto.TaskCompleteResultCO;
import org.icedamericanomall.marketing.app.task.TaskManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务完成内部端点（I49 {@code POST /internal/task/complete}，F9 每日任务体系，V5.10）
 * —— 仅集群内 Feign 调用（ia-api {@code TaskClient}：trade 确认收货 / item 评价落库旁路触发）。
 *
 * <p>{@code /internal/**} 不包装 {@code Result}，业务异常以原始 HTTP 状态 + 类型化错误体
 * {@code ErrorInfo}（{@code code / msg / category / retryable / traceId}）透出（ADR-004）。
 *
 * <p><b>降级口径（ADR-047）</b>：幂等回落不出业务异常——重复/停用/任务不存在一律
 * {@code completed=false}；调用方本就是事务外 fail-open 旁路（TaskClientFallback
 * 同样降级为 {false, 0}），主流程零感知。
 */
@RestController
@RequestMapping("/internal/task")
@RequiredArgsConstructor
public class InternalTaskController {

    private final TaskManager taskManager;

    /**
     * 触发任务完成（幂等：同用户同任务同业务日唯一）。
     *
     * @param co 完成参数（{@code TaskCompleteCO { userId 必填, taskCode 必填 }}）
     * @return 完成结果（首次=true+实发积分；重复/停用/不存在=false+0）
     */
    @PostMapping("/complete")
    public TaskCompleteResultCO complete(@RequestBody @Valid TaskCompleteCO co) {
        return taskManager.completeTask(co);
    }
}
