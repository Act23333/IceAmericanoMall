package org.icedamericanomall.trade.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Sentinel 限流 / 熔断规则装配 —— 为下单入口 {@code createOrder} 载入两条规则。
 *
 * <p><b>激活条件（V5.4 修复，P1 接线缺陷）</b>：此前写作
 * {@code @ConditionalOnProperty(name = "sentinel.enabled")}，而 yml 真正生效的属性是
 * Spring Cloud Alibaba 的 {@code spring.cloud.sentinel.enabled}（{@code spring.cloud.sentinel}
 * 下的配置项）。两者键名不一致 → 本类从未被 Spring 装配 → {@code FlowRule}/{@code DegradeRule}
 * 从未注册，{@code @SentinelResource} 形同虚设（限流熔断静默失效）。
 * 现对齐 SCA 属性键，并用 {@code matchIfMissing = true} 保持「未显式声明即启用」的既有语义。
 *
 * <p><b>规则维度</b>：
 * <ul>
 *   <li><b>flow</b>（流控）：QPS 阈值型，超出直接 block，由
 *       {@code OrderCreationManager#createOrder} 的 blockHandler 转为业务异常（HTTP 429）。</li>
 *   <li><b>degrade</b>（熔断）：异常比例型，统计窗口内异常占比越线后自动打开熔断，
 *       窗口内直接 block（同样走 blockHandler）。</li>
 * </ul>
 * {@code BlockException} 不计入 Sentinel 自身的异常统计（Sentinel 语义），无需特殊处理；
 * blockHandler 抛出的业务异常也不再回灌统计，避免「限流 → 熔断」级联误判。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.cloud.sentinel.enabled", havingValue = "true", matchIfMissing = true)
public class SentinelConfig {

    /** 受保护资源名 —— 与 {@code OrderCreationManager#RESOURCE_CREATE_ORDER}（@SentinelResource.value）一致。 */
    private static final String RESOURCE_CREATE_ORDER = org.icedamericanomall.trade.app.order.OrderCreationManager.RESOURCE_CREATE_ORDER;

    /** 流控阈值：单机 QPS 上限（压单场景下的入口保护阀值）。 */
    static final double FLOW_CREATE_ORDER_QPS = 100d;

    /** 熔断阈值：统计窗口内异常比例（0.5 = 50%）。 */
    static final double DEGRADE_CREATE_ORDER_EXCEPTION_RATIO = 0.5d;

    /** 熔断时间窗（秒）：触发后在该窗口内直接 block，窗口结束转半开试探。 */
    static final int DEGRADE_CREATE_ORDER_TIME_WINDOW_SECONDS = 10;

    /** 熔断最小请求数：窗口内样本不足此数不触发熔断，避免低流量误熔断（Sentinel 默认 5，显式声明）。 */
    static final int DEGRADE_CREATE_ORDER_MIN_REQUEST_AMOUNT = 5;

    /** 熔断统计时长（毫秒）：与时间窗对齐，保证「窗口内比例」语义直观。 */
    static final int DEGRADE_CREATE_ORDER_STAT_INTERVAL_MS = 10_000;

    /**
     * 应用启动时注册限流 + 熔断规则（Sentinel 规则默认仅存内存，进程重启后由本方法重新载入）。
     */
    @PostConstruct
    public void initRules() {
        FlowRule flowRule = new FlowRule(RESOURCE_CREATE_ORDER)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(FLOW_CREATE_ORDER_QPS);
        FlowRuleManager.loadRules(List.of(flowRule));

        DegradeRule degradeRule = new DegradeRule(RESOURCE_CREATE_ORDER)
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                .setCount(DEGRADE_CREATE_ORDER_EXCEPTION_RATIO)
                .setTimeWindow(DEGRADE_CREATE_ORDER_TIME_WINDOW_SECONDS)
                .setMinRequestAmount(DEGRADE_CREATE_ORDER_MIN_REQUEST_AMOUNT)
                .setStatIntervalMs(DEGRADE_CREATE_ORDER_STAT_INTERVAL_MS);
        DegradeRuleManager.loadRules(List.of(degradeRule));

        log.info("Sentinel rules loaded for trade-service: resource={}, flowQps={}, degradeExceptionRatio={}",
                RESOURCE_CREATE_ORDER, FLOW_CREATE_ORDER_QPS, DEGRADE_CREATE_ORDER_EXCEPTION_RATIO);
    }
}
