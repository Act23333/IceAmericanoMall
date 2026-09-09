package org.icedamericanomall.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 单条消息信息 — Java Record。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageInfo(
        String role,
        String content,
        List<ToolCallInfo> toolCalls,
        LocalDateTime timestamp
) {
    public record ToolCallInfo(String toolName, Map<String, Object> input, String output) {}
}
