package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.agent.CustomerServiceAgent;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** AI 智能客服 — FAQ问答/订单查询/人机转接 */
@RestController
@RequestMapping("/api/ai/cs")
@RequiredArgsConstructor
public class CustomerServiceController {

    private final CustomerServiceAgent agent;

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> req) {
        return Result.ok(Map.of("reply", agent.chat(req.getOrDefault("message", ""))));
    }
}
