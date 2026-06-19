package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.agent.ProductAssistantAgent;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private final ProductAssistantAgent agent;

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> req) {
        return Result.ok(Map.of("reply", agent.chat(req.getOrDefault("message", ""))));
    }
}
