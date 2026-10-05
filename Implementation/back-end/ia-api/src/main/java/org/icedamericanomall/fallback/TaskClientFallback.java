package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.TaskClient;
import org.icedamericanomall.dto.TaskCompleteCO;
import org.icedamericanomall.dto.TaskCompleteResultCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 任务完成触发降级工厂 —— <b>fail-open（纯旁路，ADR-047）</b>。
 *
 * <p>marketing 不可用时返回 {@code completed=false}（奖励静默丢失）：任务激励是体验增强，
 * 丢失只影响当日激励且不重试不补偿，交易/评价主流程零感知。
 */
@Slf4j
@Component
public class TaskClientFallback implements FallbackFactory<TaskClient> {

    @Override
    public TaskClient create(Throwable cause) {
        return new TaskClient() {

            @Override
            public TaskCompleteResultCO complete(TaskCompleteCO co) {
                log.warn("任务完成触发降级：marketing-service 不可用，任务奖励未发放"
                        + " userId={}, taskCode={}",
                        co == null ? null : co.getUserId(),
                        co == null ? null : co.getTaskCode(), cause);
                return new TaskCompleteResultCO(false, 0);
            }
        };
    }
}
