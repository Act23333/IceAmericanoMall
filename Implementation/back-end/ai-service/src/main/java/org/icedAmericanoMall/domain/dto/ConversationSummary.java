package org.icedAmericanoMall.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对话摘要 — 会话列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummary {

    private String conversationId;
    private String agentType;       // SHOPPING / CUSTOMER_SERVICE
    private String title;
    private Integer messageCount;
    private String lastMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
