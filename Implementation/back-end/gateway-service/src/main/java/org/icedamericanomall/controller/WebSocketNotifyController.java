package org.icedamericanomall.controller;

import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.event.OrderStatusChangedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * WebSocket 通知转发（内部 HTTP → WS 桥接）— 仅在 websocket.enabled=true 时激活。
 * <p>
 * 供 trade-service 在订单状态变更时调用，此处将事件推送给已连接的前端客户端。
 */
@Slf4j
@RestController
@RequestMapping("/internal/ws")
@ConditionalOnProperty(name = "websocket.enabled", havingValue = "true")
public class WebSocketNotifyController {

    @PostMapping("/order/status")
    public void notifyOrderStatus(@RequestBody OrderStatusChangedEvent event) {
        log.info("WS notify: order={} {}→{} msg={}",
                event.getOrderNo(), event.getOldStatus(), event.getNewStatus(), event.getMessage());
        // V1.2: 此处将通过 WebSocket Session 推送通知给前端
    }
}
