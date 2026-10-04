package org.icedamericanomall.ai.app.chat;

import java.security.Principal;
import java.util.Set;

/**
 * STOMP 会话身份（应用层身份载体）。
 *
 * <p>只由基础设施层的握手拦截器依据网关注入的 {@code X-User-Id}/{@code X-User-Roles}
 * 请求头构造，并注册为 STOMP 会话的 {@link Principal}；客户端在消息体里自带的
 * senderId / senderRole 一律忽略，杜绝身份伪造。
 *
 * @param userId 用户ID
 * @param roles  角色集合（网关透传，如 ROLE_SELLER、ROLE_USER）
 */
public record ChatPrincipal(Long userId, Set<String> roles) implements Principal {

    /** 卖家角色（网关透传格式）。 */
    private static final String ROLE_SELLER = "ROLE_SELLER";

    /** 消息发送者角色：卖家（chat_message.sender_role 契约值）。 */
    public static final String SENDER_ROLE_SELLER = "SELLER";

    /** 消息发送者角色：买家（chat_message.sender_role 契约值）。 */
    public static final String SENDER_ROLE_BUYER = "BUYER";

    @Override
    public String getName() {
        return String.valueOf(userId);
    }

    /**
     * 由会话角色推导消息发送者角色。
     *
     * @return {@link #SENDER_ROLE_SELLER} 或 {@link #SENDER_ROLE_BUYER}
     */
    public String senderRole() {
        return roles != null && roles.contains(ROLE_SELLER) ? SENDER_ROLE_SELLER : SENDER_ROLE_BUYER;
    }
}
