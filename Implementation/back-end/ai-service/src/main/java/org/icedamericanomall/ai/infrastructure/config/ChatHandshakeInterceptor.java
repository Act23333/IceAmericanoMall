package org.icedamericanomall.ai.infrastructure.config;

import org.icedamericanomall.common.constant.SecurityHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * STOMP 握手拦截器 —— 把网关注入的身份请求头沉淀到 WebSocket 会话属性。
 *
 * <p>WebSocket 升级请求同样经过网关，网关会注入 {@code X-User-Id} / {@code X-User-Roles}；
 * 这里只做“搬运”（握手阶段尚未建立 STOMP 会话），真正的身份装配由
 * {@link StompAuthInterceptor} 在 CONNECT 帧上完成。
 */
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    /** 会话属性键：用户ID（取值与网关注入的可信头名一致）。 */
    public static final String ATTR_USER_ID = SecurityHeaders.X_USER_ID;

    /** 会话属性键：角色（逗号分隔）。 */
    public static final String ATTR_USER_ROLES = SecurityHeaders.X_USER_ROLES;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String userId = request.getHeaders().getFirst(ATTR_USER_ID);
        if (userId != null && !userId.isBlank()) {
            attributes.put(ATTR_USER_ID, userId);
            attributes.put(ATTR_USER_ROLES, request.getHeaders().getFirst(ATTR_USER_ROLES));
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需后置处理
    }
}
