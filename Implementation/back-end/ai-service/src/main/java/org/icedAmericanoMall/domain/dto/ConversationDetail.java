package org.icedAmericanoMall.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对话详情 — 含完整消息历史。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetail {

    private String conversationId;
    private String agentType;       // SHOPPING / CUSTOMER_SERVICE
    private List<MessageInfo> messages;
}
