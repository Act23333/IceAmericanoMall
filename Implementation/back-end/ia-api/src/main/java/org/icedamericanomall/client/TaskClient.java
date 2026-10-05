package org.icedamericanomall.client;

import org.icedamericanomall.dto.TaskCompleteCO;
import org.icedamericanomall.dto.TaskCompleteResultCO;
import org.icedamericanomall.fallback.TaskClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 任务完成触发 Feign 客户端（F9 每日任务体系：trade/item → marketing-service，
 * 契约 §4.14.8c I49，V5.10）。
 *
 * <p>调用方：trade（确认收货 → DAILY_ORDER）、item（评价落库 → DAILY_REVIEW）——
 * 均**事务外旁路**调用。
 *
 * <p><b>降级语义：fail-open（纯旁路，ADR-047）</b>——任务奖励是体验激励而非交易必需，
 * marketing 不可用时降级返回 {@code completed=false}（奖励静默丢失，只影响当日激励，
 * 不重试不补偿），主流程零感知。与 I43/I44 fail-closed 刻意不同（资金/定价是事实，任务不是）。
 */
@FeignClient(name = "marketing-service", path = "/internal/task",
        contextId = "task", fallbackFactory = TaskClientFallback.class)
public interface TaskClient {

    /**
     * 触发任务完成（幂等：同用户同任务同业务日唯一，重复返回 completed=false）。
     *
     * @param co 完成参数（userId + taskCode）
     * @return 完成结果（首次=true+实发积分）
     */
    @PostMapping("/complete")
    TaskCompleteResultCO complete(@RequestBody TaskCompleteCO co);
}
