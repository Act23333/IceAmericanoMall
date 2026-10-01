package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.LogisticsClient;
import org.icedamericanomall.dto.CreateLogisticsCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogisticsClientFallback implements FallbackFactory<LogisticsClient> {

    @Override
    public LogisticsClient create(Throwable cause) {
        return new LogisticsClient() {
            @Override
            public void createLogistics(CreateLogisticsCO dto) {
                log.error("物流记录创建失败 orderId={}, cause={}", dto.getOrderId(), cause.toString());
            }
        };
    }
}
