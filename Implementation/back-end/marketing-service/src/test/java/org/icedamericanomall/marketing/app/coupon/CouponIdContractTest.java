package org.icedamericanomall.marketing.app.coupon;

import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.IdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 券业务码契约测试（ADR-050 修订）—— 创建路径的口径：
 * 空 → 系统生成 UUIDv7；已提供 → 必须 32-hex/36-hyphen 且归一为 32 位小写 hex；
 * 非法（可读编码等）→ {@code PARAM_ERROR} fail-fast（列已收紧为 BINARY(16)）。
 */
@DisplayName("CouponAdminManager.normalizeCouponId — 券业务码契约")
class CouponIdContractTest {

    @Test
    @DisplayName("空/空白 → 系统生成 32 位小写 hex（可被仓储 toBytes 往返）")
    void shouldGenerate_whenBlank() {
        String generated = CouponAdminManager.normalizeCouponId(null);
        assertTrue(generated.matches("[0-9a-f]{32}"), "生成值必须为 32 位小写 hex: " + generated);
        IdGenerator.toBytes(generated);   // 往返不抛 = 必然可落 BINARY(16)

        assertTrue(CouponAdminManager.normalizeCouponId("   ").matches("[0-9a-f]{32}"));
    }

    @Test
    @DisplayName("32-hex 输入 → 原样保留；大写 → 归一为小写")
    void shouldKeepOrLowercase_when32Hex() {
        String hex = IdGenerator.nextId();
        assertEquals(hex, CouponAdminManager.normalizeCouponId(hex));
        assertEquals(hex, CouponAdminManager.normalizeCouponId(hex.toUpperCase()));
    }

    @Test
    @DisplayName("36 位连字符 UUID → 归一为 32 位小写 hex（与存量格式兼容）")
    void shouldNormalizeHyphenatedUuid() {
        String hex = IdGenerator.nextId();
        String hyphenated = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                + "-" + hex.substring(16, 20) + "-" + hex.substring(20);

        assertEquals(hex, CouponAdminManager.normalizeCouponId(hyphenated));
    }

    @Test
    @DisplayName("可读编码（CPN-001 等非 hex）→ PARAM_ERROR fail-fast（不再可存）")
    void shouldRejectReadableCode_whenNotHex() {
        BizException e1 = assertThrows(BizException.class,
                () -> CouponAdminManager.normalizeCouponId("CPN-001"));
        assertEquals(org.icedamericanomall.common.enums.ErrorCode.PARAM_ERROR, e1.getErrorCode());

        assertThrows(BizException.class, () -> CouponAdminManager.normalizeCouponId("SUMMER2026"));
        assertThrows(BizException.class,
                () -> CouponAdminManager.normalizeCouponId("0123456789abcdef0123456789abcde"));   // 31 位
    }
}
