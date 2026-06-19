package org.icedAmericanoMall.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.config.AiProperties;
import org.icedAmericanoMall.tool.OrderQueryTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Pattern;

/**
 * AI 智能客服 — FAQ问答 + 订单查询 + 人机转接。
 * 仅在 ai.enabled=true 时激活。
 *
 * <pre>
 * Flow: 用户提问 → LLM 分类（FAQ/订单/投诉）→ 工具调用 → 生成回复
 * 低置信度 → 转人工（保存对话摘要）
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class CustomerServiceAgent {

    private final AiProperties aiProperties;
    private final OrderQueryTool orderQueryTool;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("(ORD-[A-Z0-9-]+)");

    private static final String FAQ_CONTEXT = """
            冰美商城(IceAmericanoMall)常见问题：
            - 退换货政策：签收后7天内可申请退货，15天内可换货
            - 物流时效：下单后48小时内发货，物流3-7天
            - 支付方式：支持微信支付
            - 售后流程：APP内"我的订单"→"申请售后"
            """;

    public String chat(String userMessage) {
        try {
            // Extract order number if present
            var m = ORDER_NO_PATTERN.matcher(userMessage);
            if (m.find()) {
                String result = orderQueryTool.query(m.group(1), null);
                return "您的" + result;
            }

            // FAQ mode
            return callLlm("你是冰美商城客服。" + FAQ_CONTEXT +
                    "用中文回复，简洁友善。低置信度时说'正在转接人工客服'。", userMessage);
        } catch (Exception e) {
            log.error("CS Agent error", e);
            return "抱歉，客服系统暂不可用。请拨打客服热线 400-XXX-XXXX。";
        }
    }

    @SuppressWarnings("unchecked")
    private String callLlm(String system, String user) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", aiProperties.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        body.put("temperature", 0.5);

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
        return "转接人工客服中...";
    }
}
