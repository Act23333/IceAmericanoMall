package org.icedAmericanoMall.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 对话响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {

    /** AI 回复内容 */
    private String reply;

    /** 会话ID，用于后续对话继续 */
    private String conversationId;
}
