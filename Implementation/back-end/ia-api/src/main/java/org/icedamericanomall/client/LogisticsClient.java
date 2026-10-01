package org.icedamericanomall.client;

import org.icedamericanomall.dto.CreateLogisticsCO;
import org.icedamericanomall.fallback.LogisticsClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for logistics-service internal endpoints.
 */
@FeignClient(name = "logistics-service", path = "/internal/logistics", contextId = "logistics",
        fallbackFactory = LogisticsClientFallback.class)
public interface LogisticsClient {

    @PostMapping("/create")
    void createLogistics(@RequestBody CreateLogisticsCO dto);
}
