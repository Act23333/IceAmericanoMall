package org.icedamericanomall.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 网关限流配置 — 仅在 sentinel.enabled=true 时激活。
 * <p>
 * 降级方案：sentinel.enabled=false（默认）→ 使用现有 Gateway RequestRateLimiter + @RateLimit AOP。
 * 网关适配器（SentinelGatewayFilter）需要 sentinel-spring-cloud-gateway-adapter 依赖，V1.1 暂不引入。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "sentinel.enabled", havingValue = "true")
public class SentinelConfig {

    @PostConstruct
    public void initRules() {
        List<FlowRule> rules = new ArrayList<>();

        // Auth endpoints rate limit
        rules.add(new FlowRule("gateway-auth")
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(50));

        // User endpoints rate limit
        rules.add(new FlowRule("gateway-user")
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(200));

        FlowRuleManager.loadRules(rules);
        log.info("Sentinel gateway rules loaded");
    }
}
