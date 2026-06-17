package org.icedAmericanoMall.client;

import org.icedAmericanoMall.dto.CreateLogisticsDTO;
import org.icedAmericanoMall.fallback.LogisticsClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for logistics-service internal endpoints.
 */
@FeignClient(name = "logistics-service", path = "/internal/logistics", fallbackFactory = LogisticsClientFallback.class)
public interface LogisticsClient {

    @PostMapping("/create")
    void createLogistics(@RequestBody CreateLogisticsDTO dto);
}
