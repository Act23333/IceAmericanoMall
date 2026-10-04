package org.icedamericanomall.ai.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * STOMP 发送消息报文（适配层入参）—— 类名 {@code ChatSendRequest} → {@code ChatSendReq}
 * 以符合「适配层入参统一 *Req 后缀」规范；字段与校验完全不变。
 *
 * <p>刻意<b>不含</b> senderId / senderRole 字段：身份只能来自 STOMP 会话（网关注入头解析），
 * 客户端即使多传这些字段也会被 Jackson 忽略。
 */
@Data
public class ChatSendReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID（buyer_{buyerId}_seller_{sellerId}_product_{productId}）。 */
    @NotBlank(message = "会话ID不能为空")
    private String conversationId;

    /** 接收者用户ID。 */
    @NotNull(message = "接收者不能为空")
    private Long receiverId;

    /** 消息内容。 */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 内容类型：TEXT / IMAGE / PRODUCT_CARD / ORDER_CARD（缺省 TEXT）。 */
    private String contentType;
}
