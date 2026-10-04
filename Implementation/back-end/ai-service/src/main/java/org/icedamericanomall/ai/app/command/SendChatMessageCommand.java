package org.icedamericanomall.ai.app.command;

/**
 * 发送会话消息命令（应用层入参）。
 *
 * <p>发送者身份（senderId / senderRole）由 STOMP 会话身份推导后填入，
 * 绝不取自客户端报文。
 *
 * <p><b>归位说明</b>：由 {@code app/chat/} 迁入 {@code app/command/}，应用层入参统一收口
 * （信封语义的对象放 command/，身份载体 {@code ChatPrincipal} 留在 {@code app/chat/}）。
 *
 * @param conversationId 会话ID
 * @param senderId       发送者用户ID（服务端解析）
 * @param senderRole     发送者角色 BUYER/SELLER（服务端解析）
 * @param receiverId     接收者用户ID
 * @param content        消息内容
 * @param contentType    内容类型（TEXT/IMAGE/PRODUCT_CARD/ORDER_CARD），null 视为 TEXT
 */
public record SendChatMessageCommand(
        String conversationId,
        Long senderId,
        String senderRole,
        Long receiverId,
        String content,
        String contentType
) {}
