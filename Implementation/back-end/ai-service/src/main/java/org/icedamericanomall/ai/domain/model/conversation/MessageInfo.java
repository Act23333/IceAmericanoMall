package org.icedamericanomall.ai.domain.model.conversation;

import java.time.LocalDateTime;

/**
 * 单条消息信息 — Java Record（会话详情出参的消息项）。
 *
 * <p><b>字段修剪</b>：移除 {@code toolCalls}（Agent 工具调用明细，属内部诊断信息且当前实现
 * 恒为 null，从不出现在报文中）——对外只保留 role / content / timestamp 三个字段。
 */
public record MessageInfo(
        String role,
        String content,
        LocalDateTime timestamp
) {}
