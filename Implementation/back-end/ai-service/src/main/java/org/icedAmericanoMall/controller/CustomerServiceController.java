package org.icedAmericanoMall.controller;

import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.agent.CustomerServiceAssistant;
import org.icedAmericanoMall.config.AiProperties;
import org.noLazy.common.domain.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 智能客服 — LangChain4j AiServices + @Tool (FAQ + 订单查询 + 转人工)。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/cs")
public class CustomerServiceController {

    @Autowired
    private AiProperties aiProperties;

    @Autowired(required = false)
    private CustomerServiceAssistant customerServiceAssistant;

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> req) {
        String message = req.getOrDefault("message", "");
        String reply;
        if (aiProperties.isEnabled() && customerServiceAssistant != null) {
            reply = customerServiceAssistant.chat(message);
        } else {
            reply = "AI 客服未启用。请设置 ai.enabled=true 并配置 DEEPSEEK_API_KEY。";
        }
        return Result.ok(Map.of("reply", reply));
    }
}
