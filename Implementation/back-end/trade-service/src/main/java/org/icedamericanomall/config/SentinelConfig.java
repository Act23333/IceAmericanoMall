package org.icedamericanomall.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * Sentinel 限流熔断规则 — 仅在 sentinel.enabled=true 时激活。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "sentinel.enabled", havingValue = "true")
public class SentinelConfig {

    @PostConstruct
    public void initFlowRules() {
        FlowRule rule = new FlowRule("createOrder")
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(100);
        FlowRuleManager.loadRules(Collections.singletonList(rule));

        DegradeRule degradeRule = new DegradeRule("createOrder")
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                .setCount(0.5)
                .setTimeWindow(10);
        DegradeRuleManager.loadRules(Collections.singletonList(degradeRule));

        log.info("Sentinel rules loaded for trade-service");
    }
}
