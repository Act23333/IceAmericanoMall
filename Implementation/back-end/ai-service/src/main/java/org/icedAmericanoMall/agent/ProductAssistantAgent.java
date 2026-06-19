package org.icedAmericanoMall.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.config.AiProperties;
import org.icedAmericanoMall.tool.ProductSearchTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class ProductAssistantAgent {

    private final AiProperties aiProperties;
    private final ProductSearchTool searchTool;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final Pattern TOOL_PATTERN =
            Pattern.compile("product_search\\(\"([^\"]+)\"(?:,\\s*(\\d+))?\\)");

    private static final String SYSTEM_PROMPT =
            "You are a shopping assistant for IceAmericanoMall. Use product_search(keyword, categoryId) to find products. Reply in Chinese with price in ¥.";

    public String chat(String userMessage) {
        try {
            String llmResponse = callLlm(SYSTEM_PROMPT, userMessage, true);
            if (llmResponse.contains("product_search(")) {
                String result = executeTool(llmResponse);
                return callLlm(SYSTEM_PROMPT,
                        "User: " + userMessage + "\nResults:\n" + result + "\nSummarize helpfully.", false);
            }
            return llmResponse;
        } catch (Exception e) {
            log.error("AI error", e);
            return "AI 助手暂不可用，请稍后重试。";
        }
    }

    private String executeTool(String llmResponse) {
        var m = TOOL_PATTERN.matcher(llmResponse);
        if (m.find()) {
            Long cat = m.group(2) != null ? Long.valueOf(m.group(2)) : null;
            return searchTool.search(m.group(1), cat);
        }
        return "工具调用失败";
    }

    @SuppressWarnings("unchecked")
    private String callLlm(String system, String user, boolean tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", aiProperties.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        body.put("temperature", 0.7);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + aiProperties.getApiKey());
        headers.set("Content-Type", "application/json");

        var resp = restTemplate.postForEntity(
                aiProperties.getBaseUrl() + "/chat/completions",
                new HttpEntity<>(body, headers), Map.class);

        if (resp.getBody() != null) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
            if (choices != null && !choices.isEmpty()) {
                return (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
            }
        }
        return "No response";
    }
}
