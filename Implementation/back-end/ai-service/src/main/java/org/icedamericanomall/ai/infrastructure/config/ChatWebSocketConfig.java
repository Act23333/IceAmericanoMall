package org.icedamericanomall.ai.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 买家-商家聊天 STOMP WebSocket 配置（大厂标准：淘宝旺旺/京东咚咚对标）——
 * 随客服域自 user-service 迁入（ADR-016 收尾）。
 * <p>
 * STOMP 端点: /ws/chat，SockJS 兼容降级。
 * 消息代理: 内置 SimpleBroker（单机），生产环境迁移到 RabbitMQ STOMP plugin。
 * 人工会话与 AI 客服（转人工逻辑在 {@code CsManager}）同属本服务的客服域。
 * <p>
 * 安全：握手来源走 {@code ai.websocket.allowed-origins} 白名单（不再放行任意来源）；
 * CONNECT 帧由 {@link StompAuthInterceptor} 装配可信会话身份。
 */
@Configuration
@RequiredArgsConstructor
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AiProperties aiProperties;
    private final StompAuthInterceptor stompAuthInterceptor;

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
                .setAllowedOriginPatterns(aiProperties.getWebsocket().getAllowedOrigins().toArray(String[]::new))
                .addInterceptors(new ChatHandshakeInterceptor())
                .withSockJS(); // SockJS 降级兼容（不支持 WebSocket 的浏览器）
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // CONNECT 帧装配可信身份，缺失即拒绝
        registration.interceptors(stompAuthInterceptor);
    }
}
