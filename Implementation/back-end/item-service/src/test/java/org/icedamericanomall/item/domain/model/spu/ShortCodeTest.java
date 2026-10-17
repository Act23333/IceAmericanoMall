package org.icedamericanomall.item.domain.model.spu;

import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.integration.auth.client.WechatMiniApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 短码值对象测试（ADR-063 §决策 8）—— 锁定发号口径、形态校验与<b>承载位容量预算</b>。
 *
 * <p>本类同时是「守卫 6：承载位容量预算」的落点之一：{@code scene = "p=" + 短码} 必须
 * ≤ 微信 {@code SCENE_MAX_LENGTH}（32），而 32-hex 业务键加同一前缀会超限 ——
 * 这组断言就是「短码为什么必须存在」的可执行证据。
 */
@DisplayName("ShortCode —— 发号 / 形态 / 容量预算")
class ShortCodeTest {

    @Test
    @DisplayName("newCode — 10 位小写 hex（40 bit），且是纯随机：10000 次抽样不重复")
    void shouldGenerateTenLowerHex() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String code = ShortCode.newCode();
            assertEquals(ShortCode.LENGTH, code.length());
            assertTrue(code.matches("^[0-9a-f]{10}$"), code);
            codes.add(code);
        }
        // 40 bit 熵下 1 万次抽样几乎不可能出现重复（若重复说明生成器退化成弱随机/有时间序）
        assertTrue(codes.size() > 9_990, "抽样重复过多，疑似随机源退化: " + codes.size());
    }

    @Test
    @DisplayName("newCode — 与业务键无派生关系（不含其任何 4 位连续子串）")
    void shouldNotDeriveFromBusinessId() {
        String businessId = Spu.newBusinessId();
        String code = ShortCode.newCode();
        for (int i = 0; i + 4 <= code.length(); i++) {
            assertFalse(businessId.contains(code.substring(i, i + 4)),
                    "短码不得复用业务键子串（可反解）: " + code);
        }
    }

    @Test
    @DisplayName("require — 合法形态原样返回（8–10 位小写 hex，含首尾空白归一）")
    void shouldAcceptValidForm() {
        assertEquals("8f3a1c9d20", ShortCode.require("8f3a1c9d20"));
        assertEquals("8f3a1c9d", ShortCode.require("8f3a1c9d"));
        assertEquals("8f3a1c9d20", ShortCode.require("  8f3a1c9d20  "));
    }

    @Test
    @DisplayName("require — 形态非法 → 400 PARAM_ERROR fail-fast（不是「查不到」的 404）")
    void shouldRejectMalformed() {
        assertParamError(null);
        assertParamError("   ");
        assertParamError("8f3a1c9");              // 7 位（短于下限）
        assertParamError("8f3a1c9d201");          // 11 位（长于上限）
        assertParamError("8F3A1C9D20");           // 大写（列 collation 大小写不敏感 ⇒ 只许单一形态）
        assertParamError("8f3a1c9d2g");           // 非 hex
        assertParamError("../../etc/passwd");     // 路径注入形态
    }

    @Test
    @DisplayName("isValid — 读侧过滤用：不抛异常，非法一律 false")
    void shouldNotThrowInIsValid() {
        assertTrue(ShortCode.isValid("8f3a1c9d20"));
        assertFalse(ShortCode.isValid(null));
        assertFalse(ShortCode.isValid(""));
        assertFalse(ShortCode.isValid("ZZZZZZZZ"));
    }

    @Test
    @DisplayName("容量预算 — scene（'p=' + 短码）= 12 字符 ≤ 微信上限 32；业务键形态 34 字符超限")
    void shouldFitSceneBudget() {
        String scene = "p=" + ShortCode.newCode();
        assertEquals(12, scene.length());
        assertTrue(scene.length() <= WechatMiniApiClient.SCENE_MAX_LENGTH,
                "scene 超微信上限即渠道直接拒单");
        // 反证：这就是「承载位用业务键会超限」的算式（ADR-063 §决策 7 表 #2）
        assertTrue(("p=" + Spu.newBusinessId()).length() > WechatMiniApiClient.SCENE_MAX_LENGTH);
    }

    @Test
    @DisplayName("列宽预算 — 短码 10 位 ≤ spu.short_code VARCHAR(16)（为升到 16 位留位）")
    void shouldFitColumnWidth() {
        assertTrue(ShortCode.MAX_LENGTH <= 16);
        assertTrue(ShortCode.newCode().length() <= 16);
    }

    /** 「9 位」其实合法（8–10 位区间）—— 单独断言，避免把区间读成定长。 */
    @Test
    @DisplayName("require — 8–10 位区间内均合法（不是定长 10）")
    void shouldAcceptWholeRange() {
        assertEquals("8f3a1c9d2", ShortCode.require("8f3a1c9d2"));
    }

    private static void assertParamError(String code) {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> ShortCode.require(code));
        assertEquals(400, ex.getErrorCode().getHttpStatus().value());
        assertEquals(1101, ex.getErrorCode().getCode());
    }
}
