package org.icedamericanomall.trade.domain.service;

import org.icedamericanomall.trade.domain.service.PostageCalculator.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PostageCalculator} 运费计价纯函数单元测试 —— <b>逐条锁定 F2 五条 BDD 场景</b>
 * （契约 {@code 05-API-Specification.md} §4.4.9 / 数据模型 04 §十二 12.2）。
 *
 * <p>纯函数不依赖任何框架，直接 new 用例对象调用，无需 Spring / Mock。
 */
@DisplayName("PostageCalculator — 运费计价纯函数（F2 BDD 场景）")
class PostageCalculatorTest {

    // ==================== BDD: 满包邮阈值生效 ====================

    @Test
    @DisplayName("F2-1 满包邮阈值生效 — 总额 ≥ freeThreshold(>0) → postFee = 0")
    void shouldReturnZeroPostage_whenTotalReachesFreeThreshold() {
        // Given 启用模板 fee=800、freeThreshold=9900，订单商品总额 10000
        List<Rule> rules = List.of(new Rule(800, 9900));

        // Then postFee = 0
        assertEquals(0, PostageCalculator.calculate(rules, 10000));
    }

    @Test
    @DisplayName("F2-1 边界 — 总额 = freeThreshold → 免邮（≥ 而非 >）")
    void shouldReturnZeroPostage_whenTotalEqualsThreshold() {
        assertEquals(0, PostageCalculator.calculate(List.of(new Rule(800, 9900)), 9900));
    }

    @Test
    @DisplayName("F2-1 反向 — 总额 < freeThreshold → 收固定运费")
    void shouldChargeFee_whenTotalBelowThreshold() {
        assertEquals(800, PostageCalculator.calculate(List.of(new Rule(800, 9900)), 9899));
    }

    @Test
    @DisplayName("F2-1 变体 — freeThreshold=0 表示不参与满额包邮 → 不论总额都收费")
    void shouldChargeFee_whenThresholdIsZero() {
        assertEquals(800, PostageCalculator.calculate(List.of(new Rule(800, 0)), 100000));
    }

    // ==================== BDD: 多商品取最高费率模板计一次 ====================

    @Test
    @DisplayName("F2-2 多商品取最高费率模板计一次 — T1(500) + T2(1200) → postFee = 1200（不叠加）")
    void shouldPickHighestFeeOnce_whenMultipleTemplatesBound() {
        // Given 同商家商品 A 绑定 T1(fee=500)、B 绑定 T2(fee=1200)，总额低于两模板任一阈值
        List<Rule> rules = List.of(new Rule(500, 0), new Rule(1200, 0));

        // Then 取最高 fee 计一次 = 1200（若叠加应为 1700）
        assertEquals(1200, PostageCalculator.calculate(rules, 10000));
    }

    @Test
    @DisplayName("F2-2 变体 — 最高 fee 模板命中满额阈值 → 免邮（阈值取自被选中者）")
    void shouldApplyThresholdOfPickedTemplate_whenHighestFeeTemplateHasThreshold() {
        // T1(fee=1200, free=5000) 被选中 → 总额 6000 ≥ 5000 → 0
        assertEquals(0, PostageCalculator.calculate(List.of(new Rule(1200, 5000), new Rule(500, 0)), 6000));
    }

    // ==================== BDD: 未绑定模板回落默认模板 ====================

    @Test
    @DisplayName("F2-3 未绑定模板回落默认模板 — 生效规则为默认模板 T0(600) → postFee = 600")
    void shouldChargeDefaultTemplateFee_whenItemHasNoBoundTemplate() {
        // 调用方已把「未绑定 → 默认模板 T0」解析为一条 Rule
        assertEquals(600, PostageCalculator.calculate(List.of(new Rule(600, 0)), 5000));
    }

    // ==================== BDD: 停用模板不参与计价（回落默认） ====================

    @Test
    @DisplayName("F2-4 停用模板不参与计价 — 生效规则为默认模板 T0(600)（已停用 T1(900) 不出现）")
    void shouldChargeDefaultTemplateFee_whenBoundTemplateDisabled() {
        // 停用模板由 I43 端点过滤（不返回），调用方回落默认模板 T0
        assertEquals(600, PostageCalculator.calculate(List.of(new Rule(600, 0)), 5000));
    }

    // ==================== BDD: 商家无任何启用模板兜底为 0 ====================

    @Test
    @DisplayName("F2-5 商家无任何启用模板 → postFee = 0（不阻断下单）")
    void shouldReturnZeroPostage_whenNoEffectiveTemplate() {
        assertEquals(0, PostageCalculator.calculate(List.of(), 10000));
        assertEquals(0, PostageCalculator.calculate(null, 10000));
    }

    @Test
    @DisplayName("F2-5 变体 — 规则存在但 fee 缺失 → 该规则被忽略 → 0")
    void shouldIgnoreRule_whenFeeIsNull() {
        assertEquals(0, PostageCalculator.calculate(java.util.Collections.singletonList(new Rule(null, 0)), 10000));
    }

    // ==================== 不变式 ====================

    @Test
    @DisplayName("不变式 — 运费恒 ≥ 0（负 fee 不产生负运费）")
    void shouldNeverReturnNegative() {
        assertEquals(0, PostageCalculator.calculate(List.of(new Rule(-100, 0)), 10000));
    }

    @Test
    @DisplayName("不变式 — 金额单位分：不产生小数/舍入（整除语义）")
    void shouldKeepCentsSemantics() {
        assertEquals(1, PostageCalculator.calculate(List.of(new Rule(1, 0)), 1));
    }
}
