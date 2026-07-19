package org.icedAmericanoMall.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 对话请求 DTO。
 * <p>
 * conversationId 可选：首次对话不传，由服务端生成 UUID；后续对话传入以继续已有会话。
 */
@Data
public class AiChatRequest {

    @NotBlank(message = "消息不能为空")
    @Size(max = 2000, message = "消息长度不能超过2000字符")
    private String message;

    /** 会话ID（可选），首次对话不传，由服务端生成 */
    private String conversationId;
}
