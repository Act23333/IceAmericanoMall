package org.icedamericanomall.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI 对话请求 — Java Record (不可变 DTO)。
 * conversationId 可选：首次对话不传，由服务端生成 UUID。
 */
public record AiChatRequest(
        @NotBlank(message = "消息不能为空")
        @Size(max = 2000, message = "消息长度不能超过2000字符")
        String message,

        String conversationId
) {}
