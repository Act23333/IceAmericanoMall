package org.icedamericanomall.trade.domain.service;

import org.icedamericanomall.trade.domain.port.PayoutNoFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工厂契约用例：**纯映射**（同入参同结果）+ 与生成器一致。
 *
 * <p>为什么单独测工厂而不只测生成器：仓储在锁内拿到的是 {@link PayoutNoFactory}，
 * 若实现被换成带状态的 lambda（例如统计次数、读时钟），单号会漂移、幂等键失效。
 * 这条用例锁住"同一入参必须同结果"。
 */
@DisplayName("出款单号工厂契约")
class PayoutNoFactoryContractTest {

    private static final String WITHDRAWAL_NO = "9f3ac1b27e0d4a56bc0d1e2f3a4b5c6d";

    @Test
    @DisplayName("工厂与生成器一致：FACTORY.create 就是 generate")
    void factoryShouldDelegateToGenerator() {
        PayoutNoFactory factory = PayoutNoGenerator.FACTORY;

        assertEquals(PayoutNoGenerator.generate(WITHDRAWAL_NO, 7), factory.create(WITHDRAWAL_NO, 7));
    }

    @Test
    @DisplayName("纯映射：反复调用结果不变（否则幂等键会漂移）")
    void shouldBePure() {
        PayoutNoFactory factory = PayoutNoGenerator.FACTORY;

        String first = factory.create(WITHDRAWAL_NO, 1);
        String second = factory.create(WITHDRAWAL_NO, 1);
        String third = factory.create(WITHDRAWAL_NO, 1);

        assertEquals(first, second);
        assertEquals(second, third);
    }

    @Test
    @DisplayName("工厂实例是静态 final，不是捕获状态的 lambda")
    void factoryShouldBeStaticConstant() throws Exception {
        var field = PayoutNoGenerator.class.getField("FACTORY");

        assertTrue(java.lang.reflect.Modifier.isStatic(field.getModifiers()), "必须静态常量");
        assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()), "必须 final，避免被替换成带状态实现");
    }
}
