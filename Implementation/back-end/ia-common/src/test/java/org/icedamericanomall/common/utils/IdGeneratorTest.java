package org.icedamericanomall.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IdGenerator} 单元测试 —— 覆盖 ADR-050 的关键不变量：
 * UUIDv7 版本/变体位、时间戳前缀趋势递增、唯一性、byte[] ↔ hex 往返、
 * 连字符容错、null 语义、非法输入、{@code secureToken} 凭据口径。
 */
@DisplayName("IdGenerator — UUIDv7 业务 ID 与安全令牌")
class IdGeneratorTest {

    /** 样本量：唯一性断言（v7 全随机位 74 位，1 万次撞概率可忽略）。 */
    private static final int SAMPLE_SIZE = 10_000;

    @Test
    @DisplayName("nextId — 输出 32 位小写 hex 无连字符（与 fastSimpleUUID 格式逐位兼容）")
    void shouldReturn32LowercaseHex_whenNextId() {
        for (int i = 0; i < 100; i++) {
            String id = IdGenerator.nextId();
            assertEquals(32, id.length(), "必须 32 字符: " + id);
            assertTrue(id.matches("[0-9a-f]{32}"), "必须小写 hex: " + id);
        }
    }

    @Test
    @DisplayName("nextId — version 位=0x7、variant 位=0b10（RFC 9562）")
    void shouldSetVersionAndVariantBits_whenNextId() {
        for (int i = 0; i < 1000; i++) {
            byte[] bytes = IdGenerator.toBytes(IdGenerator.nextId());
            assertEquals(0x70, bytes[6] & 0xF0, "byte[6] 高 4 位必须为版本号 0111");
            assertEquals(0x80, bytes[8] & 0xC0, "byte[8] 高 2 位必须为变体 10");
        }
    }

    @Test
    @DisplayName("nextId — 48 位时间戳前缀非递减（同毫秒允许相等，跨毫秒单调）")
    void shouldHaveNonDecreasingTimestampPrefix_whenGeneratedInSequence() {
        long previous = 0;
        for (int i = 0; i < 2000; i++) {
            byte[] bytes = IdGenerator.toBytes(IdGenerator.nextId());
            long timestamp = 0;
            for (int b = 0; b < 6; b++) {
                timestamp = (timestamp << 8) | (bytes[b] & 0xFFL);
            }
            assertTrue(timestamp >= previous, "时间戳前缀必须非递减: " + timestamp + " < " + previous);
            assertTrue(timestamp > 0, "时间戳必须为正");
            previous = timestamp;
        }
    }

    @Test
    @DisplayName("nextId — 连续生成 1 万次全唯一")
    void shouldBeUnique_whenGeneratedInBulk() {
        Set<String> ids = new HashSet<>(SAMPLE_SIZE * 2);
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            assertTrue(ids.add(IdGenerator.nextId()), "出现重复 ID");
        }
        assertEquals(SAMPLE_SIZE, ids.size());
    }

    @Test
    @DisplayName("toBytes → toHexString — 往返无损（32-hex 输入）")
    void shouldRoundTrip_when32HexInput() {
        for (int i = 0; i < 500; i++) {
            String id = IdGenerator.nextId();
            assertEquals(id, IdGenerator.toHexString(IdGenerator.toBytes(id)));
        }
    }

    @Test
    @DisplayName("toBytes — 36 位连字符 UUID 容错解析，结果与去连字符一致")
    void shouldParseHyphenatedUuid_when36LengthInput() {
        String hex = IdGenerator.nextId();
        String hyphenated = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
        assertArrayEquals(IdGenerator.toBytes(hex), IdGenerator.toBytes(hyphenated));
        assertEquals(hex, IdGenerator.toHexString(IdGenerator.toBytes(hyphenated)));
    }

    @Test
    @DisplayName("toBytes — 已知向量：全 F 128 位 → 16 字节全 0xFF")
    void shouldMatchKnownVector_whenAllF() {
        byte[] expected = new byte[16];
        java.util.Arrays.fill(expected, (byte) 0xFF);
        assertArrayEquals(expected, IdGenerator.toBytes("ffffffffffffffffffffffffffffffff"));
    }

    @Test
    @DisplayName("toBytes/toHexString — null 语义：null 入参返回 null")
    void shouldReturnNull_whenNullInput() {
        assertNull(IdGenerator.toBytes(null));
        assertNull(IdGenerator.toHexString(null));
    }

    @Test
    @DisplayName("toBytes — 非法输入抛 IllegalArgumentException（长度/字符/错位连字符）")
    void shouldThrow_whenIllegalInput() {
        assertThrows(IllegalArgumentException.class, () -> IdGenerator.toBytes(""));
        assertThrows(IllegalArgumentException.class, () -> IdGenerator.toBytes("0123456789abcdef0123456789abcde"));
        assertThrows(IllegalArgumentException.class, () -> IdGenerator.toBytes("0123456789abcdef0123456789abcdef0"));
        assertThrows(IllegalArgumentException.class, () -> IdGenerator.toBytes("0123456789abcdef0123456789abcdfg"));
        assertThrows(IllegalArgumentException.class, () -> IdGenerator.toBytes("01234567-89abcdef-0123-4567-89abcdef12"));
        assertThrows(IllegalArgumentException.class, () -> IdGenerator.toBytes("0123456789ABCDEF0123456789ABCDEF-"));
    }

    @Test
    @DisplayName("toBytesOrNull — 外部输入宽松语义：非法输入返回 null（等值查询落空=未命中）")
    void shouldReturnNull_whenToBytesOrNullWithIllegalInput() {
        assertNull(IdGenerator.toBytesOrNull("NONEXISTENT"));
        assertNull(IdGenerator.toBytesOrNull("ORD-1"));
        assertNull(IdGenerator.toBytesOrNull(""));
        assertNull(IdGenerator.toBytesOrNull(null));
        String id = IdGenerator.nextId();
        assertArrayEquals(IdGenerator.toBytes(id), IdGenerator.toBytesOrNull(id));
    }

    @Test
    @DisplayName("toBytes — 大写 hex 兼容解析（HexFormat 语义）")
    void shouldParseUppercaseHex() {
        String lower = IdGenerator.nextId();
        assertArrayEquals(IdGenerator.toBytes(lower), IdGenerator.toBytes(lower.toUpperCase()));
    }

    @Test
    @DisplayName("toHexString — 长度不为 16 抛 IllegalArgumentException")
    void shouldThrow_whenByteLengthNot16() {
        assertThrows(IllegalArgumentException.class, () -> IdGenerator.toHexString(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> IdGenerator.toHexString(new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> IdGenerator.toHexString(new byte[17]));
        assertThrows(IllegalArgumentException.class, () -> IdGenerator.toHexString(new byte[32]));
    }

    @Test
    @DisplayName("toHexString — 输出小写（大小写归一口径）")
    void shouldOutputLowercaseHex() {
        byte[] bytes = IdGenerator.toBytes("ABCDEF0123456789ABCDEF0123456789");
        assertEquals("abcdef0123456789abcdef0123456789", IdGenerator.toHexString(bytes));
    }

    @Test
    @DisplayName("secureToken — 64 位小写 hex，1 万次全唯一（256 位熵）")
    void shouldBe64HexUnique_whenSecureTokenInBulk() {
        Set<String> tokens = new HashSet<>(SAMPLE_SIZE * 2);
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            String token = IdGenerator.secureToken();
            assertEquals(64, token.length());
            assertTrue(token.matches("[0-9a-f]{64}"));
            assertTrue(tokens.add(token), "出现重复令牌");
        }
        assertEquals(SAMPLE_SIZE, tokens.size());
    }

    @Test
    @DisplayName("secureToken — 与 nextId 形态可区分（凭据不得误用时间有序 v7）")
    void shouldDifferFromNextId_inShape() {
        String token = IdGenerator.secureToken();
        String id = IdGenerator.nextId();
        assertEquals(64, token.length());
        assertEquals(32, id.length());
        assertFalse(token.equals(id));
        assertNotNull(token);
    }
}
