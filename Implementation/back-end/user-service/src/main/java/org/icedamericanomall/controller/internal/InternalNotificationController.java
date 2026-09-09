package org.icedamericanomall.controller.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 内部通知接口 — 供 item-service 等跨服务调用
 */
@RestController
@RequestMapping("/internal/notification")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    public void send(@RequestBody Map<String, Object> req) {
        notificationService.send(
                ((Number) req.get("recipientId")).longValue(),
                req.get("senderId") != null ? ((Number) req.get("senderId")).longValue() : null,
                (String) req.get("senderName"),
                (String) req.get("senderAvatar"),
                (String) req.get("type"),
                (String) req.get("title"),
                (String) req.get("content"),
                (String) req.get("linkUrl")
        );
    }
}
