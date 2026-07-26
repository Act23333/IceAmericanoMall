package org.icedamericanomall.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * WebSocket 配置 — 仅在 websocket.enabled=true 时激活。
 * <p>
 * V1.2 阶段提供 WebSocket 基础设施代码。由于 gate-service 使用 WebFlux，
 * 完整 STOMP 消息推送需在 servlet 容器（如 user-service:8080/ws/notify）中实现。
 * 当前版本通过 WebSocketNotifyController（内部 HTTP 接口）提供通知转发能力。
 */
@Configuration
@ConditionalOnProperty(name = "websocket.enabled", havingValue = "true")
public class WebSocketConfig {
    // V1.2 预留：Reactive WebSocketHandler 注册
    // 生产环境建议使用独立的 notification-service 或 user-service 的 WebSocket 端点
}
