package org.icedamericanomall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * V3.3: 买家-商家聊天 STOMP WebSocket 配置（大厂标准：淘宝旺旺/京东咚咚对标）。
 * <p>
 * STOMP 端点: /ws/chat，SockJS 兼容降级。
 * 消息代理: 内置 SimpleBroker（单机），生产环境迁移到 RabbitMQ STOMP plugin。
 */
@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 客户端订阅前缀（接收消息的目标）
        registry.enableSimpleBroker("/queue", "/topic");
        // 客户端发送前缀（发送消息到服务端）
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // SockJS 降级兼容（不支持 WebSocket 的浏览器）
    }
}
