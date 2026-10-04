package org.icedamericanomall.ai.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.app.chat.ChatPrincipal;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.UnauthorizedException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * STOMP 身份装配拦截器 —— CONNECT 帧上把网关透传的身份头装成会话 {@link ChatPrincipal}。
 *
 * <p>身份来源（按优先级）：握手会话属性（{@link ChatHandshakeInterceptor} 搬运的
 * {@code X-User-Id}）→ STOMP CONNECT 原生头。两者都没有时<b>拒绝连接</b>（UNAUTHORIZED），
 * 保证任何已建立会话的发送者身份都可信。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    /** 角色分隔符（网关透传格式，逗号分隔）。 */
    private static final String ROLE_SEPARATOR = ",";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        String userId = resolveRawIdentity(accessor, ChatHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null || userId.isBlank()) {
            log.warn("STOMP CONNECT 缺少用户身份，拒绝连接");
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "缺少用户身份，无法建立连接");
        }
        String rolesHeader = resolveRawIdentity(accessor, ChatHandshakeInterceptor.ATTR_USER_ROLES);
        accessor.setUser(new ChatPrincipal(parseUserId(userId), parseRoles(rolesHeader)));
        return message;
    }

    /** 先取握手会话属性，再回退到 STOMP 原生头。 */
    private static String resolveRawIdentity(StompHeaderAccessor accessor, String key) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object value = sessionAttributes.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return accessor.getFirstNativeHeader(key);
    }

    private static Long parseUserId(String rawUserId) {
        try {
            return Long.valueOf(rawUserId);
        } catch (NumberFormatException e) {
            log.warn("STOMP 用户身份格式非法，拒绝连接");
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "用户身份格式非法");
        }
    }

    private static Set<String> parseRoles(String rawRoles) {
        if (rawRoles == null || rawRoles.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(rawRoles.split(ROLE_SEPARATOR))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toSet());
    }
}
