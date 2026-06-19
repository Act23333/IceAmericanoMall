package org.icedAmericanoMall.controller;

import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.agent.ShoppingAssistant;
import org.icedAmericanoMall.config.AiProperties;
import org.noLazy.common.domain.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 商品助手 — Spring AI ChatClient + LangChain4j @Tool (ReAct Agent)。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {

    @Autowired
    private AiProperties aiProperties;

    @Autowired(required = false)
    private ShoppingAssistant shoppingAssistant;

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> req) {
        String message = req.getOrDefault("message", "");
        String reply;
        if (aiProperties.isEnabled() && shoppingAssistant != null) {
            reply = shoppingAssistant.chat(message);
        } else {
            reply = "AI 助手未启用。请设置 ai.enabled=true 并配置 DEEPSEEK_API_KEY。";
        }
        return Result.ok(Map.of("reply", reply));
    }
}
