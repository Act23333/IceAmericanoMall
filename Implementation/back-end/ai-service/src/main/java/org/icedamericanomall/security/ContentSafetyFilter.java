package org.icedamericanomall.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * V2.5.3: AI 内容安全过滤器 — 三层防御（输入→推理→输出）。
 * <p>
 * 输入层：长度限制、注入Pattern检测、敏感词过滤
 * 输出层：PII脱敏（手机号/身份证号）
 */
@Slf4j
@Component
public class ContentSafetyFilter {

    private static final int MAX_INPUT_LENGTH = 2000;
    private static final Set<String> INJECTION_PATTERNS = Set.of(
            "ignore previous instructions", "忽略之前的指令", "忽略之前的指示",
            "ignore all previous", "system prompt", "系统提示词",
            "you are now", "你现在是", "new instructions", "新指令",
            "forget everything", "忘记一切", "reset your"
    );
    private static final Set<String> SENSITIVE_WORDS = Set.of(
            "密码", "管理员", "admin password", "hack"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[\\dXx]");

    /**
     * 输入安全检查。
     *
     * @param input 用户输入
     * @return null 表示通过，非null 为拦截原因
     */
    public String checkInput(String input) {
        if (input == null || input.isBlank()) {
            return "输入不能为空";
        }
        if (input.length() > MAX_INPUT_LENGTH) {
            return "输入长度超过限制（" + MAX_INPUT_LENGTH + "字符）";
        }
        String lower = input.toLowerCase();
        for (String pattern : INJECTION_PATTERNS) {
            if (lower.contains(pattern)) {
                log.warn("Prompt injection pattern detected: {}", pattern);
                return "输入包含不被允许的指令，请重新描述您的问题";
            }
        }
        for (String word : SENSITIVE_WORDS) {
            if (lower.contains(word)) {
                log.warn("Sensitive word detected: {}", word);
                return "输入包含敏感词汇，请重新描述您的问题";
            }
        }
        return null;
    }

    /**
     * 输出安全检查 — PII 脱敏。
     *
     * @param output LLM 生成的回复
     * @return 脱敏后的回复
     */
    public String sanitizeOutput(String output) {
        if (output == null) return null;
        // 手机号脱敏: 13812345678 → 138****5678
        output = PHONE_PATTERN.matcher(output).replaceAll(mr ->
                mr.group().substring(0, 3) + "****" + mr.group().substring(7));
        // 身份证号脱敏: 保留前6后4
        output = ID_CARD_PATTERN.matcher(output).replaceAll(mr -> {
            String id = mr.group();
            return id.substring(0, 6) + "********" + id.substring(14);
        });
        return output;
    }

    /**
     * 输入长度限制（用于前端提示）。
     */
    public static int getMaxInputLength() {
        return MAX_INPUT_LENGTH;
    }
}
