package org.icedamericanomall.ai.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI 对话请求 — Java Record (不可变 DTO)。
 *
 * <p>{@code conversationId} 可选：首次对话不传，由服务端生成 UUID。
 * <p>{@code sellerId} 可选：商家端客服会话传入，用于限定知识库 RAG 检索范围
 * （仅查询该商家的 FAQ/政策/手册）；为空则不启用知识检索。
 * 该字段为<b>向后兼容的新增项</b>——旧调用方（不传）报文形状与行为完全不变。
 */
public record AiChatRequest(
        @NotBlank(message = "消息不能为空")
        @Size(max = 2000, message = "消息长度不能超过2000字符")
        String message,

        String conversationId,

        Long sellerId
) {
}
