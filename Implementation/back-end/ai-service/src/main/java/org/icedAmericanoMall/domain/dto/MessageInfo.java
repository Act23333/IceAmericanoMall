package org.icedAmericanoMall.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 单条消息信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageInfo {

    private String role;            // USER / ASSISTANT / SYSTEM / TOOL
    private String content;
    private List<ToolCallInfo> toolCalls;
    private LocalDateTime timestamp;

    /**
     * Tool 调用记录。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallInfo {
        private String toolName;
        private Map<String, Object> input;
        private String output;
    }
}
