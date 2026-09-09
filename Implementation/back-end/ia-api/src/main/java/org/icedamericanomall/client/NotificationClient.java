package org.icedamericanomall.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 通知服务 Feign 客户端 — 供 item-service/trade-service 跨服务发送通知
 */
@FeignClient(name = "user-service", path = "/internal/notification", contextId = "notification")
public interface NotificationClient {

    @PostMapping("/send")
    void send(@RequestBody Map<String, Object> req);
}
