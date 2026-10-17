package org.icedamericanomall.common.utils.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容派生摘要工具测试（{@code Sha256Util}，批次 B7b）—— 承载位「内容哈希」的唯一出口。
 *
 * <p>锁定：同内容恒得同值（对象键/会话键幂等的前提）、截断长度可控、无密钥摘要的
 * <b>禁用场景</b>在 javadoc 里写死（本类不测加密强度，只测确定性契约）。
 */
@DisplayName("Sha256Util —— 内容派生摘要（同内容恒同值 / 截断可控）")
class Sha256UtilTest {

    @Test
    @DisplayName("hex — 64 位小写 hex，且对同内容确定性")
    void shouldBeDeterministicFullHex() {
        String a = Sha256Util.hex("p=8f3a1c9d20|pages/product/detail/index");
        String b = Sha256Util.hex("p=8f3a1c9d20|pages/product/detail/index");

        assertEquals(a, b);
        assertEquals(64, a.length());
        assertTrue(a.matches("^[0-9a-f]{64}$"), a);
        // 已知向量（SHA-256("abc") 的标准值），防止实现被换成 MD5/CRC 而不自知
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256Util.hex("abc"));
    }

    @Test
    @DisplayName("hexPrefix — 取前 N 位（16 = 对象键、32 = 会话键），且前缀关系成立")
    void shouldReturnPrefix() {
        String full = Sha256Util.hex("abc");
        assertEquals(16, Sha256Util.hexPrefix("abc", 16).length());
        assertEquals(32, Sha256Util.hexPrefix("abc", 32).length());
        assertTrue(full.startsWith(Sha256Util.hexPrefix("abc", 16)));
        assertTrue(full.startsWith(Sha256Util.hexPrefix("abc", 32)));
    }

    @Test
    @DisplayName("不同内容 → 不同值（不是哈希常量的假实现）")
    void shouldDifferForDifferentInput() {
        assertNotEquals(Sha256Util.hexPrefix("scene|page", 16), Sha256Util.hexPrefix("scene|page2", 16));
    }

    @Test
    @DisplayName("null 透传 — 不 NPE（承载位上游可能缺值）")
    void shouldReturnNullForNull() {
        assertNull(Sha256Util.hex(null));
        assertNull(Sha256Util.hexPrefix(null, 16));
    }

    @Test
    @DisplayName("长度越界 → IllegalArgumentException（不许产出比 8 位更短的伪指纹）")
    void shouldRejectOutOfRangeLength() {
        assertThrows(IllegalArgumentException.class, () -> Sha256Util.hexPrefix("abc", 7));
        assertThrows(IllegalArgumentException.class, () -> Sha256Util.hexPrefix("abc", 65));
    }
}
