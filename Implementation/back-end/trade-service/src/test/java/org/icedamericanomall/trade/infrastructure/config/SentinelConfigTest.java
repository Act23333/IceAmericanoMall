package org.icedamericanomall.trade.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.icedamericanomall.trade.app.order.OrderCreationManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SentinelConfig 接线单元测试 —— 锁死「规则真的注册了」与「条件键名与 SCA 属性一致」。
 *
 * <p>回归背景（P1）：此前 {@code @ConditionalOnProperty} 用 {@code sentinel.enabled}，
 * 与 yml 的 {@code spring.cloud.sentinel.enabled} 不匹配，配置类从未装配、规则从未注册，
 * 限流熔断静默失效。两个断言分别守住「加载键名」与「规则内容」。
 */
@DisplayName("SentinelConfig 规则注册与接线键名")
class SentinelConfigTest {

    @Test
    @DisplayName("initRules — 注册 createOrder 的流控(QPS 100)与熔断(异常比例 0.5)规则")
    void shouldRegisterFlowAndDegradeRules_whenInitRules() {
        new SentinelConfig().initRules();

        List<FlowRule> flowRules = FlowRuleManager.getRules();
        FlowRule flow = flowRules.stream()
                .filter(r -> OrderCreationManager.RESOURCE_CREATE_ORDER.equals(r.getResource()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未注册 createOrder 流控规则"));
        assertEquals(RuleConstant.FLOW_GRADE_QPS, flow.getGrade(), "流控应为 QPS 模式");
        assertEquals(SentinelConfig.FLOW_CREATE_ORDER_QPS, flow.getCount(), 1e-9, "QPS 阈值应为 100");

        List<DegradeRule> degradeRules = DegradeRuleManager.getRules();
        DegradeRule degrade = degradeRules.stream()
                .filter(r -> OrderCreationManager.RESOURCE_CREATE_ORDER.equals(r.getResource()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未注册 createOrder 熔断规则"));
        assertEquals(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO, degrade.getGrade(), "熔断应为异常比例模式");
        assertEquals(SentinelConfig.DEGRADE_CREATE_ORDER_EXCEPTION_RATIO, degrade.getCount(), 1e-9,
                "异常比例阈值应为 0.5");
        assertEquals(SentinelConfig.DEGRADE_CREATE_ORDER_TIME_WINDOW_SECONDS, degrade.getTimeWindow());
        assertEquals(SentinelConfig.DEGRADE_CREATE_ORDER_MIN_REQUEST_AMOUNT, degrade.getMinRequestAmount());
    }

    @Test
    @DisplayName("条件键名 — 必须与 SCA 属性 spring.cloud.sentinel.enabled 对齐（键名不一致则规则不加载）")
    void shouldAlignConditionalPropertyKey_whenConfigured() {
        ConditionalOnProperty conditional = SentinelConfig.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(conditional, "SentinelConfig 必须由 @ConditionalOnProperty 控制装配");
        assertArrayEquals(new String[]{"spring.cloud.sentinel.enabled"}, conditional.name(),
                "键名必须为 spring.cloud.sentinel.enabled（SCA 属性）");
        assertEquals("true", conditional.havingValue());
        assertTrue(conditional.matchIfMissing(), "未显式声明时应默认启用");
    }
}
