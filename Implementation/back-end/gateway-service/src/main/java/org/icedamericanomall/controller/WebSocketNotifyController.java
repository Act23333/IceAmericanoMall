package org.icedamericanomall.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * WebSocket 通知转发（内部 HTTP → WS 桥接）— 仅在 websocket.enabled=true 时激活。
 *
 * 供 trade-service 在订单状态变更时调用，此处将事件推送给前端。
 */
@Slf4j
@RestController
@RequestMapping("/internal/ws")
@ConditionalOnProperty(name = "websocket.enabled", havingValue = "true")
public class WebSocketNotifyController {

    @PostMapping("/order/status")
    public void notifyOrderStatus(@RequestBody Map<String, Object> event) {
        log.info("WS notify: order={} {}→{} msg={}",
                event.get("orderNo"), event.get("oldStatus"),
                event.get("newStatus"), event.get("message"));
    }
}
